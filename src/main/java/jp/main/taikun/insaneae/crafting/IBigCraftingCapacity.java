package jp.main.taikun.insaneae.crafting;

import java.math.BigInteger;

/**
 * AE2クラフトCPUクラスタの正確な容量を取得するための任意API。
 *
 * <p>AE2本体の{@code CraftingCPUCluster#getAvailableStorage()}はlong固定なので、
 * クラスタ合計が{@link Long#MAX_VALUE}を超えた後も計算を続けられるよう、
 * BigIntegerの正本を別経路で公開する。</p>
 *
 * <p>Mixinが適用されない環境でも落ちないよう、利用側は必ず{@code instanceof}で
 * 確認してからキャストする。</p>
 */
public interface IBigCraftingCapacity {

    /** ストレージブロック全体を合計した正確なCPU容量。常に0以上。 */
    BigInteger insaneae$exactStorageCapacity();
}
