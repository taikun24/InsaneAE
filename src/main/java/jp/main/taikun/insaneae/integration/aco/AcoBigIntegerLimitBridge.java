package jp.main.taikun.insaneae.integration.aco;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** ACOを必須依存にせず、公開されているBigInteger実装上限を容量計算と表示へ渡す。 */
public final class AcoBigIntegerLimitBridge {
    private static final String CAPACITY_API_VERSION_FIELD = "CAPACITY_LIMIT_API_VERSION";
    private static final String ENABLED_METHOD = "isEnabled";
    private static final String MAXIMUM_AMOUNT_METHOD = "maximumSupportedAmount";
    /** CPUツールチップの幅を守るため、科学表記へ残す有効数字は4桁にする。 */
    private static final int SCIENTIFIC_SIGNIFICANT_DIGITS = 4;
    private static final AtomicReference<Optional<BigInteger>> CACHED_LIMIT =
            new AtomicReference<>();

    private AcoBigIntegerLimitBridge() {
    }

    /**
     * ログの取得を遅延させる。
     *
     * <p>この橋はACO非導入の判定だけならログ実装に触らずに終わるため、
     * ログ基盤の無い単体テストからも書式ヘルパーを直接呼べるようにしておく。</p>
     */
    private static Logger logger() {
        return LoggerFactory.getLogger("InsaneAE");
    }

    /** ACOが有効な場合だけ、公開APIが返す計画・NBT・同期の正確な上限を返す。 */
    public static Optional<BigInteger> maximumSupportedAmount() {
        Optional<BigInteger> cached = CACHED_LIMIT.get();
        // Forge Configは再起動反映なので、一度解決した値はプロセス中に再利用できる。
        if (cached != null) {
            return cached;
        }

        Optional<BigInteger> resolved = resolveMaximumSupportedAmount();
        CACHED_LIMIT.compareAndSet(null, resolved);
        return CACHED_LIMIT.get();
    }

    /** ツールチップへ全桁を展開せず、ACOの厳密な理論上限を式で表す。 */
    public static Optional<String> theoreticalMaximumDisplay() {
        Optional<BigInteger> maximum = maximumSupportedAmount();
        // ACO未導入・無効・互換APIなしの場合は、推測値を表示しない。
        if (maximum.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(formatTheoreticalMaximum(maximum.orElseThrow()));
    }

    static String formatTheoreticalMaximum(BigInteger maximum) {
        // 0以下は有効なクラフト容量ではないため、翻訳文字列へ渡さない。
        if (maximum == null || maximum.signum() <= 0) {
            throw new IllegalArgumentException("maximum must be positive");
        }

        String decimal = maximum.toString();
        boolean allNines = true;
        // 10^n-1のACO標準上限だけを、全桁展開せず厳密な式へ戻す。
        for (int index = 0; index < decimal.length(); index++) {
            if (decimal.charAt(index) != '9') {
                allNines = false;
                break;
            }
        }
        if (allNines) {
            return "10^" + decimal.length() + " - 1 B";
        }

        int significantDigits = Math.min(SCIENTIFIC_SIGNIFICANT_DIGITS, decimal.length());
        String fraction = decimal.substring(1, significantDigits);
        // 科学表記の末尾0は表示幅だけを増やすため除く。
        while (fraction.endsWith("0")) {
            fraction = fraction.substring(0, fraction.length() - 1);
        }
        String mantissa = fraction.isEmpty()
                ? decimal.substring(0, 1)
                : decimal.substring(0, 1) + "." + fraction;
        return mantissa + " × 10^" + (decimal.length() - 1) + " B";
    }

    private static Optional<BigInteger> resolveMaximumSupportedAmount() {
        ClassLoader loader = AcoBigIntegerLimitBridge.class.getClassLoader();
        Class<?> api;
        try {
            api = Class.forName(AcoClassNames.BIG_CRAFTING_ENGINE_API, false, loader);
        } catch (ClassNotFoundException | LinkageError absent) {
            // ACO未導入は通常構成なので、long互換容量へ静かに戻す。
            return Optional.empty();
        }

        try {
            int capacityApiVersion = api.getField(CAPACITY_API_VERSION_FIELD).getInt(null);
            // 容量上限APIを持たない旧ACOから、内部値を推測して読まない。
            if (capacityApiVersion < 1) {
                return unsupported("capacity limit API v" + capacityApiVersion + " is too old");
            }
            Method isEnabled = api.getMethod(ENABLED_METHOD);
            // ACOのBigIntegerバックエンドが無効なら、CPU容量として表示しない。
            if (!Boolean.TRUE.equals(isEnabled.invoke(null))) {
                logger().info(
                        "InsaneAE: ACO BigInteger backend is disabled;"
                                + " BigInteger crafting storage falls back to the long capacity");
                return Optional.empty();
            }

            Method maximumAmount = api.getMethod(MAXIMUM_AMOUNT_METHOD);
            Object value = maximumAmount.invoke(null);
            // 公開APIの戻り値が契約と異なる場合は、推測で変換しない。
            if (!(value instanceof BigInteger maximum) || maximum.signum() <= 0) {
                return unsupported(MAXIMUM_AMOUNT_METHOD + " returned " + value);
            }
            return Optional.of(maximum);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
            // ACOはあるのにAPIが引けない = 想定外の版。容量が黙って縮むので必ず知らせる。
            return unsupported(unavailable.toString());
        }
    }

    /**
     * ACOはあるのに容量上限APIが使えない場合の退避。
     *
     * <p>ここに落ちるとBigIntegerクラフトストレージは1ブロック{@code Long.MAX_VALUE}
     * バイトの通常ストレージになり、long超の注文が「容量不足」で弾かれる。
     * 症状がACO側の版差に見えないよう、理由を一度だけ警告へ残す。</p>
     */
    private static Optional<BigInteger> unsupported(String detail) {
        logger().warn(
                "InsaneAE: ACO is present but its BigInteger capacity-limit API is unavailable ({});"
                        + " BigInteger crafting storage reports only Long.MAX_VALUE bytes per block,"
                        + " so wide orders are refused as CPU_TOO_SMALL",
                detail);
        return Optional.empty();
    }
}
