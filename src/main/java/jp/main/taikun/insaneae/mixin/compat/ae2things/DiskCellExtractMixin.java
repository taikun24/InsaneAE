package jp.main.taikun.insaneae.mixin.compat.ae2things;

import jp.main.taikun.insaneae.integration.ae2things.InsaneDisk;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * AE2 ThingsのDISKは1回の取り出しを {@code Integer.MAX_VALUE} 個で打ち切る (issue #20)。
 * AE2はセルが要求より少なく返すと「それしか無い」と扱うので、クラフトの材料に21億個を超えて
 * 使えない。InsaneAEのDISKだけ打ち切りを外す。
 *
 * <p>{@code require = 1} にしてある。AE2 Thingsの更新で定数の位置が変わったときに、
 * 黙って#20に戻るのではなく、optionalのMixin設定のエラーとしてログに出すため。</p>
 */
@Pseudo
@Mixin(targets = "io.github.projectet.ae2things.storage.DISKCellInventory", remap = false)
public abstract class DiskCellExtractMixin {

    @Shadow
    @Final
    private ItemStack i;

    @ModifyConstant(method = "extract", constant = @Constant(longValue = 2147483647L), require = 1)
    private long insaneae$liftExtractCap(long cap) {
        return i.getItem() instanceof InsaneDisk ? Long.MAX_VALUE : cap;
    }
}
