package jp.main.taikun.insaneae.mixin;

import appeng.client.Point;
import appeng.client.gui.Tooltip;
import appeng.client.gui.widgets.CPUSelectionList;
import appeng.menu.me.crafting.CraftingStatusMenu.CraftingCpuListEntry;
import java.util.ArrayList;
import jp.main.taikun.insaneae.crafting.BigIntegerCapacityDisplayMarker;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** InsaneAEのBigInteger容量を、AE2 CPU一覧の無限表示へ置き換える。 */
@Mixin(value = CPUSelectionList.class, remap = false, priority = 1500)
public abstract class BigIntegerCPUSelectionListMixin {
    @Invoker("hitTestCpu")
    protected abstract CraftingCpuListEntry insaneae$hitTestCpu(Point point);

    @Inject(method = "formatStorage", at = @At("HEAD"), cancellable = true, require = 0)
    private void insaneae$formatExactCapacity(
            CraftingCpuListEntry entry,
            CallbackInfoReturnable<String> cir) {
        // CPU名に正確な容量が同期されている行だけを指数表記へ切り替える。
        BigIntegerCapacityDisplayMarker.read(entry.name())
                .map(BigIntegerCapacityDisplayMarker::format)
                .ifPresent(cir::setReturnValue);
    }

    @Inject(method = "getTooltip", at = @At("RETURN"), cancellable = true, require = 0)
    private void insaneae$replaceInfiniteCapacityTooltip(
            int mouseX,
            int mouseY,
            CallbackInfoReturnable<Tooltip> cir) {
        CraftingCpuListEntry entry = insaneae$hitTestCpu(new Point(mouseX, mouseY));
        // CPU行の外側では、AE2の既存Tooltipを変更しない。
        if (entry == null) {
            return;
        }
        String capacity = BigIntegerCapacityDisplayMarker.read(entry.name())
                .map(BigIntegerCapacityDisplayMarker::format)
                .orElse(null);
        Tooltip tooltip = cir.getReturnValue();
        // 正確なマーカーがない通常CPUも、AE2本来の表示へ戻す。
        if (capacity == null || tooltip == null) {
            return;
        }

        var content = new ArrayList<>(tooltip.getContent());
        // 形式が想定外なら、容量行を推測して別の行を壊さない。
        if (content.size() <= 1) {
            return;
        }
        Component exact = Component.literal(capacity);
        // AE2の0行目はCPU名、1行目が容量なので、無限記号を容量行だけ置き換える。
        if (content.size() > 1) {
            content.set(1, exact);
        }
        cir.setReturnValue(new Tooltip(content));
    }
}
