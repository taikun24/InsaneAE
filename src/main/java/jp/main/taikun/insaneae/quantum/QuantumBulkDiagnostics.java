package jp.main.taikun.insaneae.quantum;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import jp.main.taikun.insaneae.config.InsaneAEConfig;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/** BigInteger Bulk経路が待機した理由を、処理結果と分離して集計する。 */
public final class QuantumBulkDiagnostics {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<Reason, LongAdder> COUNTERS = new EnumMap<>(Reason.class);

    static {
        // 各診断理由に独立したLongAdderを割り当て、reason分岐の同期を避ける。
        for (Reason reason : Reason.values()) {
            COUNTERS.put(reason, new LongAdder());
        }
    }

    private QuantumBulkDiagnostics() {
    }

    public static void record(Reason reason) {
        COUNTERS.get(reason).increment();
        // 既定OFFの診断だけをログへ出し、通常稼働時のログスパムを避ける。
        if (InsaneAEConfig.logQuantumBulkDiagnostics()) {
            LOGGER.debug("InsaneAE Quantum Bulk decision: {}", reason.name());
        }
    }

    public static synchronized Map<Reason, Long> snapshot() {
        Map<Reason, Long> snapshot = new EnumMap<>(Reason.class);
        // すべての理由を同じ順序で読み出し、欠落理由を作らない。
        for (Map.Entry<Reason, LongAdder> entry : COUNTERS.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().sum());
        }
        return Map.copyOf(snapshot);
    }

    public static synchronized void resetForTests() {
        // 前のテストの診断値を全理由について初期化する。
        for (LongAdder counter : COUNTERS.values()) {
            counter.reset();
        }
    }

    public enum Reason {
        EXACT_PROVIDER_MISSING,
        EXACT_PROVIDER_CAPACITY_ZERO,
        EXACT_INPUTS_UNAVAILABLE,
        EXACT_PROVIDER_REJECTED,
        EXACT_BULK_ACCEPTED
    }
}
