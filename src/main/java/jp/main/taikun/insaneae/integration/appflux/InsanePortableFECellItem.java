package jp.main.taikun.insaneae.integration.appflux;

import com.glodblock.github.appflux.common.items.ItemPortableFECell;
import jp.main.taikun.insaneae.util.TieredNames;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 1G 以上のポータブル FE セル。
 *
 * <p>AppliedFlux の {@link ItemPortableFECell} を継承しているので、端末 GUI・内蔵電力・
 * 誘導カードによる持ち物への給電は AppliedFlux の実装が働く。容量・表示名・分解レシピの ID と、
 * 内蔵電力 (本体のポータブルセルと同じく ×8、充電速度 ×2) だけを変えている。</p>
 */
public class InsanePortableFECellItem extends ItemPortableFECell {

    private final long totalBytes;
    private final String nameKey;
    private final String tierLabel;

    public InsanePortableFECellItem(long totalBytes, int screenColor, String nameKey, String tierLabel) {
        // 第 1 引数 (KiB) は使わない (getBytes を上書きする)。0 なら AppliedFlux 側の
        // MEGA 扱い (電力 ×8 / 充電 ×2) も掛からないので、倍率はこちらで掛ける。
        // アイドル消費は MEGA Cells・本体のポータブルセルと同じく階層に依らず 1。
        super(0, 1.0, screenColor);
        this.totalBytes = totalBytes;
        this.nameKey = nameKey;
        this.tierLabel = tierLabel;
    }

    @Override
    public long getBytes(ItemStack stack) {
        return FluxCapacity.clampBytes(totalBytes);
    }

    @Override
    public Component getName(ItemStack stack) {
        return TieredNames.of(nameKey, tierLabel);
    }

    /**
     * 分解 (インベントリ内で右クリック) 時に AE2 がレシピを引くための ID。
     * AppliedFlux の実装は {@code appflux:} 名前空間を決め打ちしているので、
     * こちらの登録名 (= レシピ ID) を返し直す。
     */
    @Override
    public ResourceLocation getRecipeId() {
        return BuiltInRegistries.ITEM.getKey(this);
    }

    @Override
    public double getChargeRate(ItemStack stack) {
        return super.getChargeRate(stack) * 2;
    }

    @Override
    public double getAEMaxPower(ItemStack stack) {
        return super.getAEMaxPower(stack) * 8;
    }
}
