package jp.main.taikun.insaneae.integration.aco;

import java.lang.reflect.Method;

/**
 * ACO が<b>実際に受け付ける BigInteger の大きさ</b>を調べる。
 *
 * <h2>なぜ ExactCountLimits ではないのか</h2>
 * <p>{@code api.contract.ExactCountLimits} の {@code maximumCountBits} (既定 1,048,576 bit) は
 * <b>受け渡しの符号化 (payload / NBT) の上限</b>であって、計画エンジンが扱える上限ではない。
 * 計画側は {@code ACOConfig.getBigIntegerMaximumBits()} (既定かつ<b>上限が 54,427 bit</b>
 * = 10 進 16,384 桁。AQE の実装天井に合わせてある) で、
 * {@code Ae2BigCraftingPlanFactory} が {@code BigCountMath.requireMaximumBits(...)} で弾く。</p>
 *
 * <p>ここを取り違えて 1,048,576 bit の在庫を名乗ると、<b>ACO の計算が例外で終わり</b>
 * {@code WidePlanUnavailableException} (decline reason {@code ARITHMETIC_FAILURE}) になる。
 * しかも「セルを入れると全部のクラフトが落ちる」ように見える。</p>
 *
 * <h2>反射で読む理由</h2>
 * <p>{@code ACOConfig} は ACO の内部クラスで、公開境界 ({@code api.*}) には
 * この値を教えてくれる窓口が無い。名前で引けなければ既定値へ落とす。
 * こちらは ACO が居ない環境でも読み込まれるクラスなので、<b>ACO の型に触らない</b>。</p>
 */
public final class AcoExactLimits {

    private static final String ACO_CONFIG = "com.syaru.ae2craftingoptimizer.config.ACOConfig";

    /** ACO の設定上限。反射で引けなかったときはこれを使う。 */
    public static final int FALLBACK_MAXIMUM_BITS = 54_427;

    /** 常識的な下限。設定を極端に下げられても 0 bit にはしない。 */
    private static final int MINIMUM_BITS = 64;

    private static volatile Method cachedGetter;
    private static volatile boolean lookupDone;

    private AcoExactLimits() {
    }

    /**
     * ACO の計画エンジンが受け付ける最大ビット数。
     *
     * <p>設定は実行中に変えられるので<b>毎回読む</b> (キャッシュするのは反射の解決だけ)。</p>
     */
    public static int gameplayMaximumBits() {
        Method getter = getter();
        if (getter == null) {
            return FALLBACK_MAXIMUM_BITS;
        }
        try {
            int bits = (int) getter.invoke(null);
            return bits >= MINIMUM_BITS ? bits : FALLBACK_MAXIMUM_BITS;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return FALLBACK_MAXIMUM_BITS;
        }
    }

    /**
     * こちらから名乗ってよい大きさのビット数。上限の<b>半分</b>。
     *
     * <p>半分にしてあるのは、名乗った量がそのまま使われるとは限らないため。
     * 種類数を掛けた合計・複数のセルの合算・計算途中の足し算が上限を越えた瞬間に
     * ACO 側で弾かれるので、同じ桁数ぶんの余地を残しておく。</p>
     *
     * <p>1 を引いてあるのは {@code 2^n} の {@code bitLength()} が {@code n + 1} だから。
     * これで名乗る値の桁数がちょうど上限の半分になる。</p>
     */
    public static int advertisableBits() {
        return Math.max(MINIMUM_BITS, gameplayMaximumBits() / 2 - 1);
    }

    private static Method getter() {
        if (!lookupDone) {
            synchronized (AcoExactLimits.class) {
                if (!lookupDone) {
                    cachedGetter = resolve();
                    lookupDone = true;
                }
            }
        }
        return cachedGetter;
    }

    private static Method resolve() {
        try {
            Class<?> config = Class.forName(ACO_CONFIG, false, AcoExactLimits.class.getClassLoader());
            Method getter = config.getMethod("getBigIntegerMaximumBits");
            getter.setAccessible(true);
            return getter;
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError | SecurityException absent) {
            return null;
        }
    }
}
