package jp.main.taikun.insaneae.mixin.compat.extendedae;

import appeng.api.upgrades.IUpgradeableObject;
import jp.main.taikun.insaneae.upgrade.SpeedBoost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * ExtendedAEの回路切断機 (Circuit Cutter) にも加速カードの倍率を掛ける。
 *
 * <p>1 tickに進む量は {@code tickingRequest} が
 * {@code exec.execute(FCUtil.speedCardMap(AE2の加速カード枚数), ...)} で渡している。
 * その第1引数、つまり進捗量に掛ける。
 * {@code RecipeExecutor} のパッケージは1.20.1では {@code util.recipe}、1.21では
 * {@code util} 直下とversionsで違うので注意。</p>
 */
@Pseudo
@Mixin(targets = "com.glodblock.github.extendedae.common.tileentities.TileCircuitCutter",
        remap = false, priority = SpeedBoost.MIXIN_PRIORITY)
public abstract class ExCircuitCutterMixin {

    @ModifyArg(method = "tickingRequest(Lappeng/api/networking/IGridNode;I)"
            + "Lappeng/api/networking/ticking/TickRateModulation;",
            at = @At(value = "INVOKE",
                    //? if >=1.21 {
                    target = "Lcom/glodblock/github/extendedae/util/RecipeExecutor;"
                    //?} else {
                    /*target = "Lcom/glodblock/github/extendedae/util/recipe/RecipeExecutor;"
                    *///?}
                            + "execute(IZ)Lappeng/api/networking/ticking/TickRateModulation;"),
            index = 0, require = 0)
    private int insaneae$boostProgress(int progress) {
        return SpeedBoost.saturatingMultiply(progress,
                SpeedBoost.multiplier(((IUpgradeableObject) (Object) this).getUpgrades()));
    }
}
