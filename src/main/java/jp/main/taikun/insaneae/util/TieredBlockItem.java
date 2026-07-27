package jp.main.taikun.insaneae.util;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * 表示名をブロック側に委ねる {@link BlockItem}。
 *
 * <p>{@code BlockItem} の既定は {@code getDescriptionId()} をそのまま翻訳するので、
 * ブロックが {@code getName()} を上書きしていてもアイテムには反映されない。
 * 階層ラベル入りの名前 ({@link TieredNames}) を持つブロックにはこちらを使う。</p>
 */
public class TieredBlockItem extends BlockItem {

    public TieredBlockItem(Block block, Properties props) {
        super(block, props);
    }

    @Override
    public Component getName(ItemStack stack) {
        return getBlock().getName();
    }
}
