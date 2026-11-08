package jp.main.taikun.insaneae.mixin.compat.extendedae;

import appeng.api.upgrades.IUpgradeableObject;
import jp.main.taikun.insaneae.upgrade.SpeedBoost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * ExtendedAEのEx IOポートにも加速カードの倍率を掛ける。
 *
 * <p>{@code TileExIOPort} はAE2の {@code IOPortBlockEntity} を継承しつつ
 * {@code tickingRequest}を丸ごと上書きしている。基準値は2048で、AE2の加速カードで
 * ×2/×8/×32/×128)。AE2用の {@code IOPortBlockEntityMixin} は上書きされた側には
 * 効かないので、同じ手、つまり基準値の定数に倍率を掛ける手をこちらにも張る。</p>
 *
 * <p>{@code @Pseudo}: ExtendedAEが無ければ黙って読み飛ばされる。</p>
 */
@Pseudo
@Mixin(targets = "com.glodblock.github.extendedae.common.tileentities.TileExIOPort",
        remap = false, priority = SpeedBoost.MIXIN_PRIORITY)
public abstract class ExIOPortMixin {

    @ModifyConstant(method = "tickingRequest(Lappeng/api/networking/IGridNode;I)"
            + "Lappeng/api/networking/ticking/TickRateModulation;",
            constant = @Constant(longValue = 2048L), require = 0)
    private long insaneae$boostItemsToMove(long original) {
        // getUpgrades() は親クラス (IOPortBlockEntity) 定義なので @Shadowでは拾えない
        return SpeedBoost.saturatingMultiply(original,
                SpeedBoost.multiplier(((IUpgradeableObject) (Object) this).getUpgrades()));
    }
}
