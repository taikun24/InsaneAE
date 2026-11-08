package jp.main.taikun.insaneae.integration.advancedae;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Advanced AE向けのcompat Mixinが実際に走ったかを数える。
 *
 * <p>Mixinパッケージの中に置くと「mixin packageのクラスは直接参照できない」
 * ({@code IllegalClassLoadError}) で落ちるので、外に置くこと。</p>
 *
 * <h2>なぜ要るのか</h2>
 * <p>{@code @Pseudo} + {@code targets} の名指しは、相手のクラス名やメソッドの形が変わっても
 * エラーにならず黙って当たらなくなる。{@code require = 0}なので、なおさら気付きにくい。
 *
 * <p>「注入したメソッドがクラスに生えているか」を名前で見る検査では足りない。
 * Mixinはメソッドを先に混ぜてからinjectorを配線するので、
 * 配線に失敗してもメソッドだけは生えている。実際に、
 * {@code @Redirect} のハンドラ引数を {@code Object} で書いていたせいで</p>
 *
 * <pre>
 * InvalidInjectionException: ... has an invalid signature.
 *   Found unexpected argument type java.lang.Object at index 0
 * </pre>
 *
 * <p>で配線に失敗していたのに、名前を見るゲームテストは通っていた
 * 直し方は、引数に{@code @Coerce}を付けること。</p>
 *
 * <p>そこでハンドラ自身に数えさせ、実際のクラフトを流したあとで0でないことを
 * 検査する。走った証拠はこれしかない。</p>
 *
 * <p>{@code volatile long} の {@code ++} は読んで足して書く3手なので、クラフト計算スレッドと
 * サーバースレッドから同時に呼ばれると取りこぼす。{@link AtomicLong} にしてある。</p>
 */
public final class AaeCompatCounters {

    /** {@code AdvCraftingCpuStorageMixin} の容量の飽和が走った回数。 */
    public static final AtomicLong STORAGE_SATURATIONS = new AtomicLong();

    /** {@code AdvCraftingCpuBudgetMixin} の1 tick予算計算が走った回数。 */
    public static final AtomicLong BUDGET_CALCULATIONS = new AtomicLong();

    private AaeCompatCounters() {
    }
}
