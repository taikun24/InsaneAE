package jp.main.taikun.insaneae.integration.appflux;

import com.glodblock.github.appflux.common.items.ItemPortableMEGAFECell;
import jp.main.taikun.insaneae.util.TieredNames;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1G 以上のポータブル FE セル。
 *
 * <p>AppliedFlux の {@link ItemPortableMEGAFECell} を継承しているので、端末 GUI・内蔵電力
 * (MEGA 扱いで ×8、充電速度 ×2)・誘導カードによる持ち物への給電は AppliedFlux の実装が働く。
 * 変えているのは容量・表示名・分解レシピの ID だけ。</p>
 */
public class InsanePortableFECellItem extends ItemPortableMEGAFECell {

    private final long totalBytes;
    private final String nameKey;
    private final String tierLabel;

    public InsanePortableFECellItem(long totalBytes, int screenColor, String nameKey, String tierLabel) {
        // 第 1 引数 (KiB) は使わない (getBytes を上書きする)。
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
        return ForgeRegistries.ITEMS.getKey(this);
    }
}
