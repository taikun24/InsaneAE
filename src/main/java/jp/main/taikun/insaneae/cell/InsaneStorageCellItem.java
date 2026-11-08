package jp.main.taikun.insaneae.cell;

import appeng.api.stacks.AEKeyType;
import appeng.items.storage.BasicStorageCell;
import jp.main.taikun.insaneae.util.TieredNames;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * 2 GiBを超える容量を持つストレージセル。
 *
 * <p>AE2の {@link BasicStorageCell} をそのまま継承しているので、ドライブへの挿入・
 * セルワークベンチ、アップグレード、分解といった挙動は、AE2のものがそのまま働く。分解では右クリックで、ハウジングとコンポーネントに戻る。
 * 容量だけを {@link IHugeCellItem} 経由で
 * long化している。{@code BasicCellInventoryMixin}が参照する。</p>
 */
public class InsaneStorageCellItem extends BasicStorageCell implements IHugeCellItem {

    private final long totalBytesLong;
    private final String nameKey;
    private final String tierLabel;
    //? if >=1.21 {
    private final ItemLike coreItem;
    private final ItemLike housingItem;
    //?}

    public InsaneStorageCellItem(Item.Properties props, ItemLike coreItem, ItemLike housingItem,
            double idleDrain, long totalBytes, int bytesPerType, int totalTypes, AEKeyType keyType,
            String nameKey, String tierLabel) {
        // AE2 19.2のセル分解は右クリックでコンポーネントとハウジングに戻る。この分解は
        // コンストラクタ引数ではなくStorageCellDisassemblyRecipeというデータ駆動のレシピで定義する
        // core/housingはここでは保持だけして、datagen側で分解レシピを出力するのに使う
        //? if >=1.21 {
        super(props, idleDrain, toKilobytes(totalBytes), bytesPerType, totalTypes, keyType);
        //?} else {
        /*super(props, coreItem, housingItem, idleDrain, toKilobytes(totalBytes), bytesPerType, totalTypes, keyType);
        *///?}
        this.totalBytesLong = totalBytes;
        this.nameKey = nameKey;
        this.tierLabel = tierLabel;
    //? if >=1.21 {
        this.coreItem = coreItem;
        this.housingItem = housingItem;
    }

    /** 分解したときに返るセルコンポーネント。分解レシピの生成に使う。 */
    public ItemLike coreItem() {
        return coreItem;
    }

    /** 分解したときに返るハウジング。分解レシピの生成に使う。 */
    public ItemLike housingItem() {
        return housingItem;
    //?}
    }

    /** 表示名は階層ごとのlangキーではなく「書式キー + 階層ラベル」で作る。 */
    @Override
    public Component getName(ItemStack stack) {
        return TieredNames.of(nameKey, tierLabel);
    }

    /**
     * 親クラスに渡すint版の容量 (KiB)。実容量はMixinがlongで返すため通常は使われない、
     * 万一Mixinが効かなかった場合に破綻しないための保険値。intの範囲で頭打ちにする。
     */
    private static int toKilobytes(long totalBytes) {
        return (int) Math.min(Integer.MAX_VALUE / 1024L, totalBytes / 1024L);
    }

    @Override
    public long getTotalBytesLong(ItemStack stack) {
        return totalBytesLong;
    }
}
