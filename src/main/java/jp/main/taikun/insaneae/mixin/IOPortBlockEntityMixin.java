package jp.main.taikun.insaneae.mixin;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.blockentity.storage.IOPortBlockEntity;
import jp.main.taikun.insaneae.upgrade.SpeedBoost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * IO ポートが 1 回の tick で動かす量に加速カードの倍率を掛ける。
 *
 * <p>AE2 は基準値 256 に対して加速カード枚数で ×2/×4/×8 しているので、
 * その基準値そのものを倍にする (AE2 の加速カードとは掛け算で併用できる)。</p>
 */
@Mixin(value = IOPortBlockEntity.class, remap = false, priority = SpeedBoost.MIXIN_PRIORITY)
public abstract class IOPortBlockEntityMixin {

    @Shadow
    public abstract IUpgradeInventory getUpgrades();

    @ModifyConstant(method = "tickingRequest(Lappeng/api/networking/IGridNode;I)"
            + "Lappeng/api/networking/ticking/TickRateModulation;",
            constant = @Constant(longValue = 256L), require = 0)
    private long insaneae$boostItemsToMove(long original) {
        return SpeedBoost.saturatingMultiply(original, SpeedBoost.multiplier(getUpgrades()));
    }
}
