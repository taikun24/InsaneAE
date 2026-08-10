package jp.main.taikun.insaneae.quantum;

import appeng.api.stacks.AEKeyType;
import com.mojang.logging.LogUtils;
import jp.main.taikun.insaneae.mixin.ElapsedTimeTrackerInvoker;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 進捗カウンタ ({@code ElapsedTimeTracker}) の <b>Mod を問わない</b>呼び口。
 *
 * <p>AE2 のクラフト CPU を複製したアドオン (Advanced AE など) は、進捗カウンタまで
 * <b>同名・同シグネチャの自前コピー</b>で持っていることがある
 * (AAE 1.3.6 / 1.6.12 で確認。型が違うだけで {@code addMaxItems(long, AEKeyType)} は同一)。
 * AE2 の型に限定してしまうとそういう CPU でまとめ処理が丸ごと諦めになるので、
 * ここで型ごとに 1 回だけメソッドを探して使い回す。</p>
 *
 * <p>見つからない型は<b>加算をスキップするだけ</b> (一度警告を出す)。カウンタは
 * 進捗表示のためのものなので、表示が僅かにずれるのと引き換えにまとめ処理は生かす。</p>
 */
public final class TimeTrackerAdapter {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 型ごとに解決した addMaxItems。見つからなかった型は {@link #MISSING}。 */
    private static final Map<Class<?>, Method> METHODS = new ConcurrentHashMap<>();
    private static final Method MISSING;

    static {
        try {
            MISSING = TimeTrackerAdapter.class.getDeclaredMethod("missingSentinel");
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private TimeTrackerAdapter() {
    }

    /** {@link #MISSING} の番兵にするためだけのメソッド。呼ばない。 */
    private static void missingSentinel() {
    }

    /** {@code tracker} に {@code addMaxItems(amount, type)} を伝える。できなければ何もしない。 */
    public static void addMaxItems(Object tracker, long amount, AEKeyType type) {
        // AE2 本体のカウンタは Mixin の Invoker で直接呼べる (反射不要)。
        if (tracker instanceof ElapsedTimeTrackerInvoker invoker) {
            invoker.insaneae$addMaxItems(amount, type);
            return;
        }
        if (tracker == null) {
            return;
        }
        Method method = METHODS.computeIfAbsent(tracker.getClass(), TimeTrackerAdapter::find);
        if (method == MISSING) {
            return;
        }
        try {
            method.invoke(tracker, amount, type);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("InsaneAE: {}#addMaxItems threw; progress display may drift",
                    tracker.getClass().getName(), e);
            METHODS.put(tracker.getClass(), MISSING);
        }
    }

    private static Method find(Class<?> owner) {
        for (Class<?> c = owner; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Method method = c.getDeclaredMethod("addMaxItems", long.class, AEKeyType.class);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException notHere) {
                // 親クラスを見る
            } catch (RuntimeException e) {
                // setAccessible がモジュールに弾かれた場合など。スキップに倒す。
                LOGGER.warn("InsaneAE: cannot access {}#addMaxItems; progress display may drift",
                        owner.getName(), e);
                return MISSING;
            }
        }
        LOGGER.warn("InsaneAE: {} has no addMaxItems(long, AEKeyType); progress display may drift "
                + "for bulk-crafted items", owner.getName());
        return MISSING;
    }
}
