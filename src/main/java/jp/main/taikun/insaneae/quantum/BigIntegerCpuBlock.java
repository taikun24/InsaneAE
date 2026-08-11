package jp.main.taikun.insaneae.quantum;

/**
 * ACO の BigInteger 計画を試すための専用 CPU ブロック。
 *
 * <p>実行処理は既存の Quantum CPU と同じ {@link QuantumCpuBlockEntity} を使う。
 * BigInteger の計画・台帳を別実装に複製せず、InsaneAE と ACO の既存連携へ合流させる。</p>
 *
 * <p>専用の描画処理や空モデルは持たず、通常Quantum CPUの完成済みモデルと
 * テクスチャを専用の継承モデルJSONから流用する。</p>
 */
public class BigIntegerCpuBlock extends QuantumCpuBlock {
    public BigIntegerCpuBlock() {
        super();
    }
}
