package jp.main.taikun.insaneae.cell;

import appeng.api.stacks.AEKeyType;
import appeng.items.storage.BasicStorageCell;
import jp.main.taikun.insaneae.util.TieredNames;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * 2 GiB を超える容量を持つストレージセル。
 *
 * <p>AE2 の {@link BasicStorageCell} をそのまま継承しているので、ドライブへの挿入・
 * セルワークベンチ・アップグレード・分解 (右クリックでハウジングとコンポーネントに戻る)
 * といった挙動は AE2 のものがそのまま働く。容量だけを {@link IHugeCellItem} 経由で
 * long 化している ({@code BasicCellInventoryMixin} が参照)。</p>
 */
public class InsaneStorageCellItem extends BasicStorageCell implements IHugeCellItem {

    private final long totalBytesLong;
    private final String nameKey;
    private final String tierLabel;
    private final ItemLike coreItem;
    private final ItemLike housingItem;

    public InsaneStorageCellItem(Item.Properties props, ItemLike coreItem, ItemLike housingItem,
            double idleDrain, long totalBytes, int bytesPerType, int totalTypes, AEKeyType keyType,
            String nameKey, String tierLabel) {
        // AE2 19.2 でセルの分解 (右クリックでコンポーネント + ハウジングに戻る) は
        // コンストラクタ引数ではなく StorageCellDisassemblyRecipe というデータ駆動のレシピになった。
        // core/housing はここでは保持だけして、datagen 側で分解レシピを吐くのに使う。
        super(props, idleDrain, toKilobytes(totalBytes), bytesPerType, totalTypes, keyType);
        this.totalBytesLong = totalBytes;
        this.nameKey = nameKey;
        this.tierLabel = tierLabel;
        this.coreItem = coreItem;
        this.housingItem = housingItem;
    }

    /** 分解したときに返るセルコンポーネント (分解レシピの生成に使う)。 */
    public ItemLike coreItem() {
        return coreItem;
    }

    /** 分解したときに返るハウジング (分解レシピの生成に使う)。 */
    public ItemLike housingItem() {
        return housingItem;
    }

    /** 表示名は階層ごとの lang キーではなく「書式キー + 階層ラベル」で作る。 */
    @Override
    public Component getName(ItemStack stack) {
        return TieredNames.of(nameKey, tierLabel);
    }

    /**
     * 親クラスに渡す int 版の容量 (KiB)。実容量は Mixin が long で返すため通常は使われない、
     * 万一 Mixin が効かなかった場合に破綻しないための保険値 (int の範囲で頭打ち)。
     */
    private static int toKilobytes(long totalBytes) {
        return (int) Math.min(Integer.MAX_VALUE / 1024L, totalBytes / 1024L);
    }

    @Override
    public long getTotalBytesLong(ItemStack stack) {
        return totalBytesLong;
    }
}
