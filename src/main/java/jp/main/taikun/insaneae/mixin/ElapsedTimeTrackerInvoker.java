package jp.main.taikun.insaneae.mixin;

import appeng.api.stacks.AEKeyType;
import appeng.crafting.execution.ElapsedTimeTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 進捗表示用のカウンタ。まとめ処理でも AE2 と同じだけ加算しておく。 */
@Mixin(value = ElapsedTimeTracker.class, remap = false)
public interface ElapsedTimeTrackerInvoker {

    @Invoker("addMaxItems")
    void insaneae$addMaxItems(long amount, AEKeyType type);
}
