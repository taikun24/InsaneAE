package jp.main.taikun.insaneae.integration.appflux;

import jp.main.taikun.insaneae.util.TieredNames;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
//? if >=1.21 {
import com.glodblock.github.appflux.common.items.ItemPortableFECell;
import net.minecraft.core.registries.BuiltInRegistries;
//?}
//? if <1.21 {
/*import com.glodblock.github.appflux.common.items.ItemPortableMEGAFECell;
import net.neoforged.neoforge.registries.ForgeRegistries;
*///?}

/**
 * 1G以上のポータブルFEセル。
 *
 * <p>AppliedFluxの {@link ItemPortableFECell} を継承しているので、端末GUI・内蔵電力・
 * 誘導カードによる持ち物への給電はAppliedFluxの実装が働く。容量・表示名・分解レシピのIDと、
 * 内蔵電力は本体のポータブルセルと同じく×8、充電速度は×2で、それだけを変えている。</p>
 */
//? if >=1.21 {
public class InsanePortableFECellItem extends ItemPortableFECell {
//?} else {
/*public class InsanePortableFECellItem extends ItemPortableMEGAFECell {
*///?}

    private final long totalBytes;
    private final String nameKey;
    private final String tierLabel;

    public InsanePortableFECellItem(long totalBytes, int screenColor, String nameKey, String tierLabel) {
        // 第1引数のKiBは使わない。getBytesを上書きするため。0ならAppliedFlux側の
        // MEGA扱いの電力×8と充電×2も掛からないので、倍率はこちらで掛ける
        // アイドル消費はMEGA Cells・本体のポータブルセルと同じく階層に依らず1
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
     * インベントリ内で右クリックして分解するとき、AE2がレシピを引くためのID。
     * AppliedFluxの実装は {@code appflux:} 名前空間を決め打ちしているので、
     * こちらの登録名、つまりレシピIDを返し直す。
     */
    @Override
    public ResourceLocation getRecipeId() {
    //? if >=1.21 {
        return BuiltInRegistries.ITEM.getKey(this);
    }

    @Override
    public double getChargeRate(ItemStack stack) {
        return super.getChargeRate(stack) * 2;
    }

    @Override
    public double getAEMaxPower(ItemStack stack) {
        return super.getAEMaxPower(stack) * 8;
    //?} else {
        /*return ForgeRegistries.ITEMS.getKey(this);
    *///?}
    }
}
