package jp.main.taikun.insaneae.integration.aco;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicReference;

/** ACOを必須依存にせず、公開されているBigInteger実装上限だけを表示へ渡す。 */
public final class AcoBigIntegerLimitBridge {
    private static final String ACO_API_CLASS =
            "com.syaru.ae2craftingoptimizer.api.big.BigCraftingEngineApi";
    private static final String ACO_MATH_CLASS =
            "com.syaru.ae2craftingoptimizer.engine.BigCountMath";
    private static final String ENABLED_METHOD = "isEnabled";
    private static final String MAXIMUM_DIGITS_FIELD = "HARD_MAXIMUM_DECIMAL_DIGITS";
    private static final AtomicReference<OptionalInt> CACHED_DIGITS = new AtomicReference<>();

    private AcoBigIntegerLimitBridge() {
    }

    /** ACOが有効な場合だけ、計算・NBT・同期で共有する10進桁数の理論上限を返す。 */
    public static OptionalInt theoreticalMaximumDecimalDigits() {
        OptionalInt cached = CACHED_DIGITS.get();
        // Forge Configは再起動反映なので、一度解決した値はプロセス中に再利用できる。
        if (cached != null) {
            return cached;
        }

        OptionalInt resolved = resolveMaximumDecimalDigits();
        CACHED_DIGITS.compareAndSet(null, resolved);
        return CACHED_DIGITS.get();
    }

    /** ツールチップへ全桁を展開せず、ACOの厳密な理論上限を式で表す。 */
    public static Optional<String> theoreticalMaximumDisplay() {
        OptionalInt digits = theoreticalMaximumDecimalDigits();
        // ACO未導入・無効・互換APIなしの場合は、推測値を表示しない。
        if (digits.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(formatTheoreticalMaximum(digits.getAsInt()));
    }

    static String formatTheoreticalMaximum(int decimalDigits) {
        // 0桁以下は有効なBigInteger上限にならないため、翻訳文字列へ渡さない。
        if (decimalDigits < 1) {
            throw new IllegalArgumentException("decimalDigits must be positive");
        }
        return "10^" + decimalDigits + " - 1 B";
    }

    private static OptionalInt resolveMaximumDecimalDigits() {
        try {
            ClassLoader loader = AcoBigIntegerLimitBridge.class.getClassLoader();
            Class<?> api = Class.forName(ACO_API_CLASS, false, loader);
            Method isEnabled = api.getMethod(ENABLED_METHOD);
            // ACOのBigIntegerバックエンドが無効なら、CPU容量として表示しない。
            if (!Boolean.TRUE.equals(isEnabled.invoke(null))) {
                return OptionalInt.empty();
            }

            Class<?> math = Class.forName(ACO_MATH_CLASS, false, loader);
            Field maximumDigits = math.getField(MAXIMUM_DIGITS_FIELD);
            int digits = maximumDigits.getInt(null);
            // 壊れた連携クラスの値をそのままUIへ出さない。
            if (digits < 1) {
                return OptionalInt.empty();
            }
            return OptionalInt.of(digits);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
            return OptionalInt.empty();
        }
    }
}
