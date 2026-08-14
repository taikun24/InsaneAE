package jp.main.taikun.insaneae.quantum;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.inv.ListCraftingInventory;
import com.mojang.logging.LogUtils;
import jp.main.taikun.insaneae.integration.aco.AcoBigIntegerJobRegistry;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>AE2 のクラフト CPU を複製したクラス</b>向けの {@link CraftingJobView}。
 *
 * <p>Advanced AE の {@code AdvCraftingCPULogic} のようなアドオンの CPU は、AE2 の
 * {@code CraftingCpuLogic} をほぼそのまま写したもので、抱えているジョブも
 * <b>同名・同構造だが別のクラス</b>になっている。こちらからはその型を名指しできない
 * (アドオンをコンパイル依存に足したくないし、足したところで Mod ごとに増える) ので、
 * <b>フィールド名で辿る</b>。</p>
 *
 * <h2>反射で困らない理由</h2>
 * <p>解決は<b>クラスごとに 1 回だけ</b>で、以降は {@link Field} を使い回す。
 * 実際に読むのは {@code executeCrafting} 1 回につき数フィールドなので、
 * <b>CPU 1 台につき 1 tick に数回</b>にしかならない。置き換えようとしているのが
 * 「1 クラフトごとの材料取り出しと組み立て」であることを思えば誤差でしかない。</p>
 *
 * <h2>見つからなかったら何もしない</h2>
 * <p>フィールド構成が想定と違えば {@link #of} が null を返し、まとめ処理は<b>丸ごと諦めて</b>
 * その CPU 本来の 1 回ずつの処理に任せる。壊れるより遅いほうがましなので、
 * 推測で動かすことはしない。</p>
 */
public final class ReflectiveCraftingJobView implements CraftingJobView {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 解決に失敗したクラスもここに覚えて、毎 tick 反射で探し直さないようにする。 */
    private static final Map<Class<?>, Layout> LAYOUTS = new ConcurrentHashMap<>();
    private static final Layout UNSUPPORTED = new Layout();

    private final Layout layout;
    private final Object logic;
    private final Object job;

    private ReflectiveCraftingJobView(Layout layout, Object logic, Object job) {
        this.layout = layout;
        this.logic = logic;
        this.job = job;
    }

    /**
     * CPU のロジックから窓口を作る。
     *
     * @param logic 複製された {@code CraftingCpuLogic} 相当のインスタンス
     * @return 使えなければ null (ジョブが無い / 構造が想定と違う)
     */
    public static CraftingJobView of(Object logic) {
        if (logic == null) {
            return null;
        }
        Layout layout = LAYOUTS.computeIfAbsent(logic.getClass(), Layout::resolve);
        if (layout == UNSUPPORTED) {
            return null;
        }
        try {
            Object job = layout.job.get(logic);
            return job == null ? null : new ReflectiveCraftingJobView(layout, logic, job);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LAYOUTS.put(logic.getClass(), UNSUPPORTED);
            LOGGER.warn("InsaneAE: giving up on bulk crafting for {}", logic.getClass().getName(), e);
            return null;
        }
    }

    /** 複製CPUのprivate jobを、ACO exact台帳の同一性キーとして取得する。 */
    public static Object jobOwner(Object logic) {
        if (logic == null) {
            return null;
        }
        Layout layout = LAYOUTS.computeIfAbsent(logic.getClass(), Layout::resolve);
        if (layout == UNSUPPORTED) {
            return null;
        }
        try {
            return layout.job.get(logic);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return null;
        }
    }

    @Override
    public ListCraftingInventory getInventory() {
        return read(layout.inventory, logic);
    }

    @Override
    public ListCraftingInventory getWaitingFor() {
        return read(layout.waitingFor, job);
    }

    @Override
    public Object getTimeTracker() {
        return read(layout.timeTracker, job);
    }

    @Override
    public void markDirty() {
        try {
            Object target = layout.markDirtyOwner == null ? logic : layout.markDirtyOwner.get(logic);
            if (target != null) {
                layout.markDirty.invoke(target);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("markDirty failed", e);
        }
    }

    @Override
    public Optional<AcoBigIntegerJobRegistry.CraftingCursor> exactTasks() {
        return AcoBigIntegerJobRegistry.find(job)
                .map(exact -> exact.cursor(pattern -> {
                    @SuppressWarnings("unchecked")
                    Map<IPatternDetails, Object> tasks =
                            (Map<IPatternDetails, Object>) read(layout.tasks, job);
                    tasks.remove(pattern);
                }));
    }

    @Override
    public TaskCursor tasks() {
        return new MapTaskCursor(read(layout.tasks, job)) {
            @Override
            protected long read(Object progress) {
                try {
                    return layout.progressValue(progress).getLong(progress);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("task progress unreadable", e);
                }
            }

            @Override
            protected void write(Object progress, long value) {
                try {
                    layout.progressValue(progress).setLong(progress, value);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("task progress unwritable", e);
                }
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> T read(Field field, Object owner) {
        try {
            return (T) field.get(owner);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not read " + field, e);
        }
    }

    // ------------------------------------------------------------------ 解決

    /** 1 つの CPU クラスについて解決済みのフィールド一式。 */
    private static final class Layout {

        private Field job;
        private Field inventory;
        private Field tasks;
        private Field waitingFor;
        private Field timeTracker;
        /** {@code markDirty()} を持っている相手。ロジック自身なら null。 */
        private Field markDirtyOwner;
        private Method markDirty;
        /** タスクの残り回数。値の実体を見るまで型が分からないので後から入れる。 */
        private volatile Field progressValue;

        static Layout resolve(Class<?> logicClass) {
            try {
                Layout layout = new Layout();
                layout.job = field(logicClass, "job", Object.class);
                layout.inventory = field(logicClass, "inventory", ListCraftingInventory.class);

                Class<?> jobClass = layout.job.getType();
                layout.tasks = field(jobClass, "tasks", Map.class);
                layout.waitingFor = field(jobClass, "waitingFor", ListCraftingInventory.class);
                // 型は確かめない: Advanced AE は進捗カウンタまで自前のコピーで持っている
                // (AE2 の ElapsedTimeTracker に限定すると AAE で丸ごと弾かれる)。
                // 呼び方の差は TimeTrackerAdapter が吸収し、想定外の型でも加算を
                // スキップするだけなので、ここで弾く必要が無い。
                layout.timeTracker = field(jobClass, "timeTracker", Object.class);

                layout.resolveMarkDirty(logicClass);
                return layout;
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOGGER.info("InsaneAE: {} does not look like an AE2 crafting CPU, "
                        + "bulk crafting will not be used for it ({})",
                        logicClass.getName(), e.toString());
                return UNSUPPORTED;
            }
        }

        /**
         * {@code markDirty()} の在処を探す。AE2 は {@code cluster}、Advanced AE は {@code cpu} と
         * 名前が違うので、<b>名前ではなく「そのメソッドを持っているか」で探す</b>。
         */
        private void resolveMarkDirty(Class<?> logicClass) throws ReflectiveOperationException {
            for (Class<?> c = logicClass; c != null && c != Object.class; c = c.getSuperclass()) {
                try {
                    markDirty = c.getMethod("markDirty");
                    markDirty.setAccessible(true);
                    return;             // ロジック自身が持っていた
                } catch (NoSuchMethodException ignored) {
                    // 次はフィールドを見る
                }
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) {
                        continue;
                    }
                    try {
                        Method m = f.getType().getMethod("markDirty");
                        f.setAccessible(true);
                        m.setAccessible(true);
                        markDirtyOwner = f;
                        markDirty = m;
                        return;
                    } catch (NoSuchMethodException ignored) {
                        // 次のフィールドへ
                    }
                }
            }
            throw new NoSuchMethodException("markDirty() not reachable from " + logicClass.getName());
        }

        /** タスクの残り回数のフィールド。{@code long} でなければ使わない。 */
        Field progressValue(Object progress) throws ReflectiveOperationException {
            Field cached = progressValue;
            if (cached != null && cached.getDeclaringClass().isInstance(progress)) {
                return cached;
            }
            Field found = field(progress.getClass(), "value", long.class);
            progressValue = found;
            return found;
        }

        /**
         * 名前でフィールドを引く (継承をたどる)。{@code expected} が {@code Object} 以外なら
         * 型も確かめる。<b>型が違うなら黙って使わない</b>のが要点で、
         * 名前だけ合っている無関係なフィールドを掴むと後で不可解に壊れる。
         */
        private static Field field(Class<?> owner, String name, Class<?> expected)
                throws ReflectiveOperationException {
            for (Class<?> c = owner; c != null && c != Object.class; c = c.getSuperclass()) {
                Field f;
                try {
                    f = c.getDeclaredField(name);
                } catch (NoSuchFieldException notHere) {
                    continue;           // 親クラスを見る
                }
                if (expected != Object.class && f.getType() != expected
                        && !expected.isAssignableFrom(f.getType())) {
                    throw new NoSuchFieldException(name + " on " + c.getName() + " is "
                            + f.getType().getName() + ", expected " + expected.getName());
                }
                f.setAccessible(true);
                return f;
            }
            throw new NoSuchFieldException(name + " on " + owner.getName());
        }
    }
}
