package jp.main.taikun.insaneae.upgrade;

import appeng.items.materials.UpgradeCardItem;
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

/** 何倍速かをツールチップに出す加速カード。 */
public class InsaneSpeedCardItem extends UpgradeCardItem {

    private final InsaneSpeedCardType type;

    public InsaneSpeedCardItem(Properties props, InsaneSpeedCardType type) {
        super(props);
        this.type = type;
    }

    public InsaneSpeedCardType type() {
        return type;
    }

    @Override
    //? if >=1.21 {
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines,
            TooltipFlag flag) {
    //?} else {
    /*public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
    *///?}
        lines.add(Component.translatable("item.insaneae.speed_card.tooltip", type.multiplier())
                .withStyle(ChatFormatting.GRAY));
        //? if >=1.21 {
        super.appendHoverText(stack, context, lines, flag);
        //?} else {
        /*super.appendHoverText(stack, level, lines, flag);
        *///?}
    }
}
