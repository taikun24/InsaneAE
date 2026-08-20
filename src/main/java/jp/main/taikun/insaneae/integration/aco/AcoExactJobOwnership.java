package jp.main.taikun.insaneae.integration.aco;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;

/**
 * ACO が「正確な BigInteger の実機実行」として<b>そのジョブを所有しているか</b>を判定する。
 *
 * <h2>なぜ要るのか</h2>
 * <p>ACO 1.5.23 で、AAE 専用だった exact 実行が AE2 の {@code CraftingCPUCluster} にも付いた
 * ({@code mixin/Ae2ExactCraftingLogicMixin})。ACO は exact ジョブの間
 * {@code executeCrafting} を @HEAD で 0 に打ち切り、{@code insert} も 0 で拒否して、
 * 実行と納品を丸ごと自分の {@code PhysicalCraftingTreeTransaction} で持つ。</p>
 *
 * <p>ところが<b>同じ @HEAD にこちらのまとめ処理も刺さっている</b>。実機のスタックトレースで
 * 確認した Mixin の適用順は</p>
 * <pre>insaneae:CraftingCpuLogicMixin → insaneae:CraftingCpuBudgetMixin → aco:Ae2ExactCraftingLogicMixin</pre>
 * <p>で、<b>こちらのほうが先に走る</b>。ACO が打ち切る前にタスクを進めてしまうと、
 * ACO の台帳に無い実行になって二重計上・隔離のもとになる。所有者が居るなら降りる。</p>
 *
 * <h2>なぜ反射なのか</h2>
 * <p>ACO の型を名指ししたくないのが半分。もう半分は<b>相手のインタフェースが 2 つある</b>から:
 * AE2 のジョブは {@code access.ExactCraftingJobAccess}、Advanced AE のジョブは
 * {@code access.AdvancedAeExactCraftingJobAccess} を実装する。<b>メソッド名は同じ</b>
 * ({@code aco$isExactJob}) なので、名前で引けば両方に効く。</p>
 *
 * <p>ACO が居ない・古い環境ではメソッドが生えていないので、常に false を返す
 * (= 従来どおりこちらが実行する)。解決はクラスごとに 1 回だけ覚える。</p>
 */
public final class AcoExactJobOwnership {

    /** ACO が exact ジョブへ生やす判定メソッド。AE2 版・AAE 版で共通。 */
    private static final String METHOD_NAME = "aco$isExactJob";

    /** ジョブのクラスごとの解決結果。{@link Optional#empty()} は「生えていない」。 */
    private static final Map<Class<?>, Optional<Method>> METHODS = new ConcurrentHashMap<>();

    /**
     * ACO 所有のジョブを実際に見た回数。<b>観測用で、動作には使わない。</b>
     *
     * <p>降りたかどうかは結果を見ても分からない (どちらの経路でも最終的な見た目は同じ) ので、
     * ゲームテストから「判定が実際に走って true を返したか」を見るための唯一の手掛かりになる。
     * 名前が生えているかではなく<b>実際に走ったか</b>で検査すること。</p>
     */
    public static volatile long observedAcoOwnedJobs;

    private AcoExactJobOwnership() {
    }

    /**
     * このジョブを ACO の exact 実行が所有しているか。
     *
     * @param job CPU が抱えている実行中ジョブ (AE2 の {@code ExecutingCraftingJob} でも、
     *            アドオンが複製した同名クラスでもよい)。null なら false。
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
            // 従来どおりこちらが実行するほうが害が小さい。
            METHODS.put(job.getClass(), Optional.empty());
            return false;
        }
    }

    private static Optional<Method> resolve(Class<?> type) {
        try {
            // Mixin が足すインタフェース実装は public なので getMethod で届く。
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
