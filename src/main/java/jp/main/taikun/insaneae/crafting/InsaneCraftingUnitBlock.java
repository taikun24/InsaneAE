package jp.main.taikun.insaneae.crafting;

import appeng.block.crafting.CraftingUnitBlock;
import appeng.block.crafting.ICraftingUnitType;
import jp.main.taikun.insaneae.util.TieredNames;
import net.minecraft.network.chat.MutableComponent;

/**
 * クラフトストレージ／協調処理ユニットのブロック。
 *
 * <p>中身は AE2 の {@link CraftingUnitBlock} そのままで、<b>表示名だけ</b>
 * 「書式キー + 階層ラベル」から作る ({@link TieredNames})。
 * アイテム側は {@link jp.main.taikun.insaneae.util.TieredBlockItem} が
 * ここの {@link #getName()} を引く。</p>
 */
public class InsaneCraftingUnitBlock extends CraftingUnitBlock {

    private final String nameKey;
    private final String tierLabel;

    public InsaneCraftingUnitBlock(ICraftingUnitType type, String nameKey, String tierLabel) {
        super(type);
        this.nameKey = nameKey;
        this.tierLabel = tierLabel;
    }

    @Override
    public MutableComponent getName() {
        return TieredNames.of(nameKey, tierLabel);
    }
}
