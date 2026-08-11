package jp.main.taikun.insaneae.quantum;

/**
 * ACO の BigInteger 計画を試すための専用 CPU ブロック。
 *
 * <p>実行処理は既存の Quantum CPU と同じ {@link QuantumCpuBlockEntity} を使う。
 * BigInteger の計画・台帳を別実装に複製せず、InsaneAE と ACO の既存連携へ合流させる。</p>
 *
 * <p>実験用ブロックであることを明確にするため、専用テクスチャは割り当てず、
 * Minecraft標準の{@code minecraft:missingno}を意図的に表示する。</p>
 */
public class BigIntegerCpuBlock extends QuantumCpuBlock {
    public BigIntegerCpuBlock() {
        super();
    }
}
