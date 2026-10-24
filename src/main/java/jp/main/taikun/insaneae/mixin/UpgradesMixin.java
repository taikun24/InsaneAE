package jp.main.taikun.insaneae.mixin;

import appeng.api.upgrades.Upgrades;
import jp.main.taikun.insaneae.upgrade.UpgradeMirrors;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * AE2 の機械へのカード登録を、超次元版にも複製させる → {@link UpgradeMirrors}。
 *
 * <p>引数 3 つの {@code add} は 4 つの方を呼ぶだけなので、こちらだけ見張れば全部の登録が通る。
 * 失敗しても「超次元版にカードが挿せない」だけなので、required=false の
 * {@code insaneae.compat.mixins.json} に置いている。</p>
 */
@Mixin(value = Upgrades.class, remap = false)
public abstract class UpgradesMixin {

    @Inject(method = "add(Lnet/minecraft/world/level/ItemLike;Lnet/minecraft/world/level/ItemLike;ILjava/lang/String;)V",
            at = @At("TAIL"))
    private static void insaneae$mirrorToInsaneMachines(ItemLike upgradeCard, ItemLike upgradableObject,
            int maxSupported, @Nullable String tooltipGroup, CallbackInfo ci) {
        UpgradeMirrors.onUpgradeAdded(upgradeCard, upgradableObject, maxSupported, tooltipGroup);
    }
}
