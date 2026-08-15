package jp.main.taikun.insaneae.compat;

/**
 * Advanced AE 向けの compat Mixin が<b>実際に走ったか</b>を数える。
 *
 * <p>Mixin パッケージの中に置くと「mixin package のクラスは直接参照できない」
 * ({@code IllegalClassLoadError}) で落ちるので、<b>外に置くこと</b>。</p>
 *
 * <h2>なぜ要るのか</h2>
 * <p>{@code @Pseudo} + {@code targets} の名指しは、相手のクラス名やメソッドの形が変わっても
 * <b>エラーにならず黙って当たらなくなる</b> ({@code require = 0} なので尚更)。
 *
 * <p>「注入したメソッドがクラスに生えているか」を名前で見る検査では<b>足りない</b>。
 * Mixin はメソッドを先に混ぜてから injector を配線するので、
 * <b>配線に失敗してもメソッドだけは生えている</b>。実際 2026-08-15 に、
 * {@code @Redirect} のハンドラ引数を {@code Object} で書いていたせいで</p>
 *
 * <pre>
 * InvalidInjectionException: ... has an invalid signature.
 *   Found unexpected argument type java.lang.Object at index 0
 * </pre>
 *
 * <p>で配線に失敗していたのに、名前を見るゲームテストは<b>通っていた</b>
 * (直し方は引数に {@code @Coerce} を付けること)。</p>
 *
 * <p>そこでハンドラ自身に数えさせ、<b>実際のクラフトを流したあとで 0 でないこと</b>を
 * 検査する。走った証拠はこれしかない。</p>
 */
public final class AaeCompatCounters {

    /** {@code AdvCraftingCpuStorageMixin} の容量の飽和が走った回数。 */
    public static volatile long storageSaturations;

    /** {@code AdvCraftingCpuBudgetMixin} の 1 tick 予算計算が走った回数。 */
    public static volatile long budgetCalculations;

    private AaeCompatCounters() {
    }
}
