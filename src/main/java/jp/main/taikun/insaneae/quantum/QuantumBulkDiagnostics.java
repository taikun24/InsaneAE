package jp.main.taikun.insaneae.quantum;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import jp.main.taikun.insaneae.config.InsaneAEConfig;

/** Quantum CPUのExact窓が辞退・受理された理由を集計する診断用カウンタ。 */
public final class QuantumBulkDiagnostics {
    private static final EnumMap<Reason, LongAdder> COUNTERS = new EnumMap<>(Reason.class);

    static {
        // 全理由を先に登録し、診断OFF時にも動的なMap構造変更を起こさない。
        for (Reason reason : Reason.values()) {
            COUNTERS.put(reason, new LongAdder());
        }
    }

    private QuantumBulkDiagnostics() {
    }

    public static void record(Reason reason) {
        if (!InsaneAEConfig.logQuantumBulkDiagnostics() || reason == null) {
            return;
        }
        COUNTERS.get(reason).increment();
    }

    public static synchronized Map<Reason, Long> snapshotAndReset() {
        EnumMap<Reason, Long> snapshot = new EnumMap<>(Reason.class);
        // サーバー管理者が一回の診断取得で比較できるよう、全理由をゼロ込みで返す。
        for (Map.Entry<Reason, LongAdder> entry : COUNTERS.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().sumThenReset());
        }
        return Map.copyOf(snapshot);
    }

    public enum Reason {
        EXACT_PROVIDER_MISSING,
        EXACT_PROVIDER_CAPACITY_ZERO,
        EXACT_INPUTS_UNAVAILABLE,
        EXACT_PROVIDER_REJECTED,
        EXACT_BULK_ACCEPTED
    }
}
