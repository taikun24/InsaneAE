package jp.main.taikun.insaneae.mixin;

import appeng.api.networking.ticking.TickRateModulation;
import appeng.me.service.TickManagerService;
import appeng.me.service.helpers.TickTracker;
import jp.main.taikun.insaneae.upgrade.SpeedBoost;
import jp.main.taikun.insaneae.upgrade.TickBoost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 加速カードを挿した機械に「同じ tick のうちの追い tick」を入れる。
 *
 * <p>AE2 の tick 配送はすべて {@code unsafeTickingRequest} を通るので、ここ 1 箇所に
 * 割り込めば機械ごとに Mixin を書かなくてよい。どの機械を何回回すかは
 * {@link TickBoost} 側の判断 (待ち時間で頭打ちになる機械だけ)。</p>
 */
@Mixin(value = TickManagerService.class, remap = false, priority = SpeedBoost.MIXIN_PRIORITY)
public abstract class TickManagerServiceMixin {

    @Inject(method = "unsafeTickingRequest(Lappeng/me/service/helpers/TickTracker;I)"
            + "Lappeng/api/networking/ticking/TickRateModulation;",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void insaneae$burst(TickTracker tracker, int ticksSinceLastCall,
            CallbackInfoReturnable<TickRateModulation> cir) {
        TickRateModulation first = cir.getReturnValue();
        TickRateModulation last = TickBoost.burst(tracker.getGridTickable(), tracker.getNode(), first);
        if (last != first) {
            cir.setReturnValue(last);
        }
    }
}
