package jp.main.taikun.insaneae.upgrade;

import appeng.items.materials.UpgradeCardItem;
import jp.main.taikun.insaneae.util.StatTooltip;
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
 * Quantum CPU専用のタスク統合カード。
 *
 * <p>装着すると、まとめ処理1回、つまり1パターン × Nクラフトが、クラフトCPUの
 * 1 tick予算を1操作しか消費しなくなる。まとめ1回は実際に1回の機械往復なので、
 * これは帳簿を実体に合わせる変更で、クラフト回数の上限はQuantum CPU自身の予算
 * 加速カード枚数が受け持つ形になる。</p>
 */
public class TaskFusionCardItem extends UpgradeCardItem {

    public TaskFusionCardItem(Properties props) {
        super(props);
    }

    @Override
    //? if >=1.21 {
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines,
            TooltipFlag flag) {
    //?} else {
    /*public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
    *///?}
        StatTooltip.add(lines, Component.translatable("item.insaneae.task_fusion_card.tooltip"));
        //? if >=1.21 {
        super.appendHoverText(stack, context, lines, flag);
        //?} else {
        /*super.appendHoverText(stack, level, lines, flag);
        *///?}
    }
}
