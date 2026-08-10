package jp.main.taikun.insaneae.mixin.compat;

import appeng.api.upgrades.IUpgradeableObject;
import appeng.blockentity.AEBaseBlockEntity;
import jp.main.taikun.insaneae.upgrade.SpeedBoost;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * ExtendedAE の拡張分子組立装置 (Ex Molecular Assembler) にも加速カードの倍率を掛ける。
 *
 * <p>組み立ての進捗は各 {@code CraftingThread} の {@code userPower(ticks, bonus, tax)} が
 * 決めている (AE2 の分子組立装置と同じ形)。AE2 用の
 * {@code MolecularAssemblerBlockEntityMixin} と同じく、{@code bonusValue} 引数に掛ける。
 * どの機械のスレッドかは {@code host} フィールド (AE2 の {@code AEBaseBlockEntity} 型) で分かる。</p>
 */
@Pseudo
@Mixin(targets = "com.glodblock.github.extendedae.common.me.CraftingThread",
        remap = false, priority = SpeedBoost.MIXIN_PRIORITY)
public abstract class ExCraftingThreadMixin {

    @Shadow
    @Final
    protected AEBaseBlockEntity host;

    @ModifyVariable(method = "userPower(IID)I", at = @At("HEAD"), ordinal = 1, argsOnly = true,
            require = 0)
    private int insaneae$boostBonus(int bonusValue) {
        return host instanceof IUpgradeableObject upgradeable
                ? SpeedBoost.saturatingMultiply(bonusValue, SpeedBoost.multiplier(upgradeable.getUpgrades()))
                : bonusValue;
    }
}
