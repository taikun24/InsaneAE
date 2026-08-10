package jp.main.taikun.insaneae.mixin.compat;

import appeng.api.upgrades.IUpgradeableObject;
import jp.main.taikun.insaneae.upgrade.SpeedBoost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * ExtendedAE の回路切断機 (Circuit Cutter) にも加速カードの倍率を掛ける。
 *
 * <p>1 tick に進む量は {@code tickingRequest} が
 * {@code exec.execute(FCUtil.speedCardMap(AE2の加速カード枚数), ...)} で渡している。
 * その第 1 引数 (進捗量) に掛ける。
 * {@code RecipeExecutor} のパッケージは 1.20.1 では {@code util.recipe}、1.21 では
 * {@code util} 直下と versions で違うので注意。</p>
 */
@Pseudo
@Mixin(targets = "com.glodblock.github.extendedae.common.tileentities.TileCircuitCutter",
        remap = false, priority = SpeedBoost.MIXIN_PRIORITY)
public abstract class ExCircuitCutterMixin {

    @ModifyArg(method = "tickingRequest(Lappeng/api/networking/IGridNode;I)"
            + "Lappeng/api/networking/ticking/TickRateModulation;",
            at = @At(value = "INVOKE",
                    target = "Lcom/glodblock/github/extendedae/util/RecipeExecutor;"
                            + "execute(IZ)Lappeng/api/networking/ticking/TickRateModulation;"),
            index = 0, require = 0)
    private int insaneae$boostProgress(int progress) {
        return SpeedBoost.saturatingMultiply(progress,
                SpeedBoost.multiplier(((IUpgradeableObject) (Object) this).getUpgrades()));
    }
}
