package jp.main.taikun.insaneae.upgrade;

import appeng.items.materials.UpgradeCardItem;
import jp.main.taikun.insaneae.quantum.QuantumCpuBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
//? if >=1.21 {
import net.minecraft.world.item.Item;
//?}
//? if <1.21 {
/*import net.minecraft.world.level.Level;
*///?}

/**
 * Quantum CPU 専用の加速カード。1 枚ごとに組み立て速度が
 * {@link QuantumCpuBlockEntity#MULTIPLIER_PER_CARD} 倍になる。
 */
public class QuantumAccelerationCardItem extends UpgradeCardItem {

    public QuantumAccelerationCardItem(Properties props) {
        super(props);
    }

    @Override
    //? if >=1.21 {
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines,
            TooltipFlag flag) {
    //?} else {
    /*public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
    *///?}
        lines.add(Component.translatable("item.insaneae.quantum_acceleration_card.tooltip",
                QuantumCpuBlockEntity.MULTIPLIER_PER_CARD).withStyle(ChatFormatting.GRAY));
        //? if >=1.21 {
        super.appendHoverText(stack, context, lines, flag);
        //?} else {
        /*super.appendHoverText(stack, level, lines, flag);
        *///?}
    }
}
