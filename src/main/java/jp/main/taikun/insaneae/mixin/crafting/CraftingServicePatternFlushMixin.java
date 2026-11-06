package jp.main.taikun.insaneae.mixin.crafting;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;
import jp.main.taikun.insaneae.provider.InsanePatternProviderLogic;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.Future;

/**
 * クラフト計算を始める前に、超次元パターンプロバイダーが溜めているパターン更新を流す。
 *
 * <p>計算の開始時にグリッドのクラフト索引を写し取る Mod (ACO 2.0) がいると、
 * 同じ tick に入れたパターンが索引に載る前に計算が走ってしまうため。</p>
 */
@Mixin(value = CraftingService.class, remap = false)
public abstract class CraftingServicePatternFlushMixin {

    @Inject(method = "beginCraftingCalculation", at = @At("HEAD"))
    private void insaneae$flushPendingPatterns(Level level, ICraftingSimulationRequester requester, AEKey what,
            long amount, CalculationStrategy strategy, CallbackInfoReturnable<Future<ICraftingPlan>> cir) {
        InsanePatternProviderLogic.flushAllPending();
    }
}
