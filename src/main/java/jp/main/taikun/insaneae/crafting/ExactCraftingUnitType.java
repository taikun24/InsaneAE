package jp.main.taikun.insaneae.crafting;

import appeng.block.crafting.ICraftingUnitType;

import java.math.BigInteger;

/**
 * AE2のlong互換容量とは別に、正確なBigInteger容量を持つクラフトユニット型。
 *
 * <p>AE2本体へは{@link #getStorageBytes()}のlong値を返し、InsaneAEのクラスタMixinだけが
 * {@link #exactStorageBytes()}を合計する。これにより標準クラフトCPU構造と実行経路を維持したまま、
 * longを超える容量を連携Modへ公開できる。</p>
 */
public interface ExactCraftingUnitType extends ICraftingUnitType {

    /** このクラフトユニット一個ぶんの、丸める前の正確な容量。 */
    BigInteger exactStorageBytes();
}
