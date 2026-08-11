package jp.main.taikun.insaneae.crafting;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * BigInteger容量を、AE2のCPU名同期へ固定長で埋め込む表示専用マーカー。
 *
 * <p>AE2のCPU一覧は容量をlongで受け取るため、実値そのものではなく
 * 「10進桁数」と「先頭桁」だけを同期する。これでクライアント側は容量を
 * {@code 仮数 × 10^指数} として再構成でき、無限記号へ丸めずに表示できる。</p>
 */
public final class BigIntegerCapacityDisplayMarker {
    /** AQEのCPU表示経路とも共有できるよう、表示マーカーは固定識別子にする。 */
    private static final String PREFIX = "insaneae:big_integer_capacity_v1=";
    /** Componentツリーを無制限にたどらないための走査上限。 */
    private static final int MAX_COMPONENTS_TO_SCAN = 64;
    private BigIntegerCapacityDisplayMarker() {
    }

    public static Component mark(Component name, BigInteger capacity) {
        // 名前や容量が未確定なら、元の名前をそのまま返して表示を壊さない。
        if (name == null || capacity == null || capacity.signum() < 0) {
            return name;
        }

        BigIntegerCapacityDisplayValue value = BigIntegerCapacityDisplayValue.capture(capacity);
        // 同じ容量のマーカーが既にあればComponentの兄弟を増やさない。
        if (read(name).filter(value::equals).isPresent()) {
            return name;
        }

        MutableComponent marked = name.copy();
        // 容量更新のたびに古い不可視マーカーを連結し続けない。
        marked.getSiblings().removeIf(BigIntegerCapacityDisplayMarker::isMarker);
        marked.append(Component.empty().withStyle(style ->
                style.withInsertion(PREFIX + value.encode())));
        return marked;
    }

    public static Optional<BigIntegerCapacityDisplayValue> read(Component component) {
        // 通常CPU名にはマーカーがないため、表示上書きを行わない。
        if (component == null) {
            return Optional.empty();
        }

        Deque<Component> pending = new ArrayDeque<>();
        pending.add(component);
        int scanned = 0;
        BigIntegerCapacityDisplayValue latest = null;
        // 外部から渡されたComponentでも、表示処理の走査量を固定する。
        while (!pending.isEmpty() && scanned++ < MAX_COMPONENTS_TO_SCAN) {
            Component current = pending.removeFirst();
            String insertion = current.getStyle().getInsertion();
            // 最新の正常なマーカーだけを採用し、壊れた値は表示へ反映しない。
            if (insertion != null && insertion.startsWith(PREFIX)) {
                Optional<BigIntegerCapacityDisplayValue> decoded = BigIntegerCapacityDisplayValue.decode(
                        insertion.substring(PREFIX.length()));
                if (decoded.isPresent()) {
                    latest = decoded.orElseThrow();
                }
            }
            pending.addAll(current.getSiblings());
        }
        return Optional.ofNullable(latest);
    }

    /** マーカーの値をCPU一覧・ツールチップへ表示する。 */
    public static String format(BigIntegerCapacityDisplayValue value) {
        return value.format();
    }

    private static boolean isMarker(Component component) {
        String insertion = component.getStyle().getInsertion();
        return insertion != null && insertion.startsWith(PREFIX);
    }

}
