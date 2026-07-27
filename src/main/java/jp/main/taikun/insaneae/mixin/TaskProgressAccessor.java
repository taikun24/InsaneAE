package jp.main.taikun.insaneae.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@code ExecutingCraftingJob.TaskProgress} の残り回数。
 * クラス自体がパッケージプライベートなので {@code targets} で名指しする。
 */
@Mixin(targets = "appeng.crafting.execution.ExecutingCraftingJob$TaskProgress", remap = false)
public interface TaskProgressAccessor {

    @Accessor("value")
    long insaneae$getValue();

    @Accessor("value")
    void insaneae$setValue(long value);
}
