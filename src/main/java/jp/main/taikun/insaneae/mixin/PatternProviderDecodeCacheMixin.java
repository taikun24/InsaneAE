package jp.main.taikun.insaneae.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.helpers.patternprovider.PatternProviderLogic;
import jp.main.taikun.insaneae.provider.InsanePatternProviderLogic;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 大量枠のプロバイダで、パターンを 1 枚変えるたびに<b>全枠をデコードし直す</b>のをやめる (issue #16)。
 *
 * <p>AE2 の {@code updatePatterns} は全スロットを {@code PatternDetailsHelper.decodePattern} し直す。
 * 1620 枠ではこれだけで 1 回 30ms ほどかかる。デコード結果は {@link InsanePatternProviderLogic} が
 * 覚えているので、そちらに問い合わせる。AE2 素のプロバイダはそのまま。</p>
 *
 * <p>compat 側 (required=false) に置いてある。他 Mod が同じ呼び出しを差し替えていて当たらなくても、
 * 毎回デコードする元の遅い経路に戻るだけで動作は変わらない。</p>
 */
@Mixin(value = PatternProviderLogic.class, remap = false)
public abstract class PatternProviderDecodeCacheMixin {

    @Redirect(method = "updatePatterns()V",
            at = @At(value = "INVOKE",
                    target = "Lappeng/api/crafting/PatternDetailsHelper;decodePattern("
                            + "Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;)"
                            + "Lappeng/api/crafting/IPatternDetails;"),
            require = 0)
    private IPatternDetails insaneae$decodeCached(ItemStack stack, Level level) {
        if ((Object) this instanceof InsanePatternProviderLogic logic) {
            return logic.decodeCached(stack, level);
        }
        return PatternDetailsHelper.decodePattern(stack, level);
    }
}
