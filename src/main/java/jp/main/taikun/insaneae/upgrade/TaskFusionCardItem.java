package jp.main.taikun.insaneae.upgrade;

import appeng.items.materials.UpgradeCardItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Quantum CPU 専用のタスク統合カード。
 *
 * <p>装着すると、まとめ処理 1 回 (1 パターン × N クラフト) がクラフト CPU の
 * 1 tick 予算を <b>1 操作しか消費しなくなる</b>。まとめ 1 回は実際に 1 回の機械往復なので、
 * これは帳簿を実体に合わせる変更で、クラフト回数の上限は Quantum CPU 自身の予算
 * (加速カード枚数) が受け持つ形になる。</p>
 */
public class TaskFusionCardItem extends UpgradeCardItem {

    public TaskFusionCardItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.insaneae.task_fusion_card.tooltip")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, level, lines, flag);
    }
}
