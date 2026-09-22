package jp.main.taikun.insaneae.iface;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * 超特大インターフェイスのアイテム。
 *
 * <p>置いてあるブロックの名前は {@link InsaneInterfaceBlock#getName()} が返すが、
 * <b>持ち物やクリエイティブタブでの名前はアイテム側が決める</b>
 * ({@code ItemStack#getHoverName} → {@code Item#getName(ItemStack)} で、
 * 既定は {@code getDescriptionId} をそのまま引くだけ)。
 * ここを揃えないと、別名の起動でブロックとアイテムの名前が食い違う。</p>
 */
public class InsaneInterfaceBlockItem extends BlockItem {

    public InsaneInterfaceBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return InsaneInterfaceNames.of(InsaneInterfaceNames.BLOCK_KEY);
    }
}
