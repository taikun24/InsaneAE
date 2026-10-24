package jp.main.taikun.insaneae.upgrade;

import appeng.items.materials.UpgradeCardItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

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
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.insaneae.speed_card.tooltip", type.multiplier())
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, level, lines, flag);
    }
}
