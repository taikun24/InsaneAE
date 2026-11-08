package jp.main.taikun.insaneae.mixin.compat.extendedae;

import appeng.api.upgrades.IUpgradeableObject;
import jp.main.taikun.insaneae.upgrade.SpeedBoost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * ExtendedAEの水晶組立機、Crystal Assemblerは1.21で追加された。加速カードの倍率を掛ける。
 *
 * <p>仕組みは回路切断機と同じで、{@code tickingRequest} が
 * {@code exec.execute(FCUtil.speedCardMap(AE2の加速カード枚数), ...)} に渡す
 * 第1引数、つまり進捗量に掛ける。</p>
 */
@Pseudo
@Mixin(targets = "com.glodblock.github.extendedae.common.tileentities.TileCrystalAssembler",
        remap = false, priority = SpeedBoost.MIXIN_PRIORITY)
public abstract class ExCrystalAssemblerMixin {

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
