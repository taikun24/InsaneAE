package jp.main.taikun.insaneae.cell;

import appeng.api.stacks.AEKeyType;
import appeng.items.storage.StorageTier;
import appeng.items.tools.powered.PortableCellItem;
import appeng.menu.me.common.MEStorageMenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 2 GiB を超える容量を持つポータブルセル。
 *
 * <p>AE2 の {@link PortableCellItem} をそのまま継承しているので、端末 GUI・内蔵電力・
 * アップグレード・染色・分解は AE2 の実装が働く。容量だけを {@link IHugeCellItem} 経由で
 * long 化している ({@code BasicCellInventoryMixin} が参照)。</p>
 */
public class InsanePortableCellItem extends PortableCellItem implements IHugeCellItem {

    private final StorageTier tier;
    private final long totalBytesLong;
    private final String nameKey;
    private final String tierLabel;

    public InsanePortableCellItem(Properties props, StorageTier tier, long totalBytes, int totalTypes,
            AEKeyType keyType, MenuType<MEStorageMenu> menu, int defaultColor,
            String nameKey, String tierLabel) {
        // AE2 の既定上限 (63) を超える型数は BasicCellInventoryMixin が有効化する。
        super(keyType, totalTypes, menu, tier, props.stacksTo(1), defaultColor);
        this.tier = tier;
        this.totalBytesLong = totalBytes;
        this.nameKey = nameKey;
        this.tierLabel = tierLabel;
    }

    /** 表示名は階層ごとの lang キーではなく「書式キー + 階層ラベル」で作る。 */
    @Override
    public net.minecraft.network.chat.Component getName(ItemStack stack) {
        return jp.main.taikun.insaneae.util.TieredNames.of(nameKey, tierLabel);
    }

    @Override
    public StorageTier getTier() {
        return tier;
    }

    @Override
    public long getTotalBytesLong(ItemStack stack) {
        return totalBytesLong;
    }

    /** MEGA Cells と同じく、上位ポータブルセルでもアイドル消費は一定。 */
    @Override
    public double getIdleDrain() {
        return 1.0;
    }

    /**
     * 分解 (インベントリ内で右クリック) 時に AE2 がレシピを引くための ID。
     * 本 Mod のレシピは {@code data/insaneae/recipes/<アイテム名>.json} に置いてあるので、
     * 登録名がそのままレシピ ID になる。
     */
    @Override
    public ResourceLocation getRecipeId() {
        return ForgeRegistries.ITEMS.getKey(this);
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
