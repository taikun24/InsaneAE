package jp.main.taikun.insaneae.integration.aco;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;

/**
 * ACOが「正確なBigIntegerの実機実行」としてそのジョブを所有しているかを判定する。
 *
 * <h2>なぜ要るのか</h2>
 * <p>ACO 1.5.23で、AAE専用だったexact実行がAE2の {@code CraftingCPUCluster} にも付いた
 * ({@code mixin/Ae2ExactCraftingLogicMixin})。ACOはexactジョブの間
 * {@code executeCrafting} を @HEADで0に打ち切り、{@code insert} も0で拒否して、
 * 実行と納品を丸ごと自分の {@code PhysicalCraftingTreeTransaction} で持つ。</p>
 *
 * <p>ところが同じ @HEADにこちらのまとめ処理も注入されている。実機のスタックトレースで
 * 確認したMixinの適用順は</p>
 * <pre>insaneae:CraftingCpuLogicMixin → insaneae:CraftingCpuBudgetMixin → aco:Ae2ExactCraftingLogicMixin</pre>
 * <p>で、こちらのほうが先に走る。ACOが打ち切る前にタスクを進めてしまうと、
 * ACOの台帳に無い実行になって二重計上・隔離のもとになる。所有者が居るなら処理をACOに任せる。</p>
 *
 * <h2>なぜ反射なのか</h2>
 * <p>ACOの型を名指ししたくないのが半分。もう半分は相手のインタフェースが2つあるから:
 * AE2のジョブは {@code access.ExactCraftingJobAccess}、Advanced AEのジョブは
 * {@code access.AdvancedAeExactCraftingJobAccess} を実装する。メソッド名は同じ
 * ({@code aco$isExactJob}) なので、名前で引けば両方に使える。</p>
 *
 * <p>ACOが居ない・古い環境ではメソッドが生えていないので、常にfalseを返す
 * つまり、こちらが実行する。解決はクラスごとに1回だけ覚える。</p>
 */
public final class AcoExactJobOwnership {

    /** ACOがexactジョブへ生やす判定メソッド。AE2版・AAE版で共通。 */
    private static final String METHOD_NAME = "aco$isExactJob";

    /** ジョブのクラスごとの解決結果。{@link Optional#empty()} は「生えていない」。 */
    private static final Map<Class<?>, Optional<Method>> METHODS = new ConcurrentHashMap<>();

    /**
     * ACO所有のジョブを実際に見た回数。観測用で、動作には使わない。
     *
     * <p>降りたかどうかは結果を見ても分からない。どちらの経路でも最終的な見た目は同じなので、
     * ゲームテストから「判定が実際に走ってtrueを返したか」を見るための唯一の手掛かりになる。
     * 名前が生えているかではなく実際に走ったかで検査すること。</p>
     */
    public static volatile long observedAcoOwnedJobs;

    private AcoExactJobOwnership() {
    }

    /**
     * このジョブをACOのexact実行が所有しているか。
     *
     * @param job CPUが抱えている実行中ジョブ。AE2の{@code ExecutingCraftingJob}でも、
     *            アドオンが複製した同名クラスでもよい)。nullならfalse。
     */
    public static boolean isAcoOwned(@Nullable Object job) {
        if (job == null) {
            return false;
        }
        Optional<Method> method = METHODS.computeIfAbsent(job.getClass(), AcoExactJobOwnership::resolve);
        if (method.isEmpty()) {
            return false;
        }
        try {
            boolean owned = Boolean.TRUE.equals(method.get().invoke(job));
            if (owned) {
                observedAcoOwnedJobs++;
            }
            return owned;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            // 判定できないなら「所有されていない」に倒す。降りすぎてクラフトが止まるより、
            // こちらが実行するほうが害が小さい
            METHODS.put(job.getClass(), Optional.empty());
            return false;
        }
    }

    private static Optional<Method> resolve(Class<?> type) {
        try {
            // Mixinが足すインタフェース実装はpublicなのでgetMethodで届く
            Method method = type.getMethod(METHOD_NAME);
            if (method.getReturnType() != boolean.class) {
                return Optional.empty();
            }
            return Optional.of(method);
        } catch (NoSuchMethodException | RuntimeException absent) {
            return Optional.empty();
        }
    }
}
