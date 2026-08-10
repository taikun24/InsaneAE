package jp.main.taikun.insaneae.integration.aco;

import appeng.api.stacks.AEKey;
import java.math.BigInteger;
import java.util.Map;

/**
 * Quantum CPUの完成品待ちをlongへ縮めずに扱う内部境界。
 *
 * <p><b>永続化はこのインターフェースの責務ではない。</b>保存は {@link PendingOutputNbt} が
 * {@link #snapshot()} から書き、読み込みは {@link #add} へ流し込む。台帳の実装 (ACO / 内蔵) に
 * 保存形式を任せると、あとから実装が切り替わったとき (ACO を抜いたときなど) に
 * 自分のセーブデータが読めなくなるため。</p>
 *
 * <p>実装はどのメソッドからも<b>例外を投げてはいけない</b>。呼び出し元は serverTick と
 * クラフト処理の真ん中なので、投げるとサーバーティックごと落ちる。不正な引数は黙って無視する。</p>
 */
public interface PendingOutputLedger {
    /** 正の量を積む。null や 0 以下は黙って無視する。 */
    void add(AEKey key, BigInteger amount);

    /** {@code maximum} を上限に取り出し、取り出せた量を返す (常に 0 以上)。 */
    long drain(AEKey key, long maximum);

    /** 現在の中身のコピー。呼び出し後の変更は反映されない。 */
    Map<AEKey, BigInteger> snapshot();

    boolean isEmpty();

    void clear();
}
