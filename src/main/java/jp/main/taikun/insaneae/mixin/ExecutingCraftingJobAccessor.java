package jp.main.taikun.insaneae.mixin;

import appeng.crafting.execution.ElapsedTimeTracker;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** 実行中クラフトジョブの内部 (パッケージプライベート) への入口。まとめ処理の帳簿更新に使う。 */
@Mixin(value = ExecutingCraftingJob.class, remap = false)
public interface ExecutingCraftingJobAccessor {

    /** パターン → 残り回数。値は {@link TaskProgressAccessor} 経由で読み書きする。 */
    @Accessor("tasks")
    Map<Object, Object> insaneae$getTasks();

    /** 完成待ちのアイテム。ここに入れておかないと CPU がクラフト結果を受け取らない。 */
    @Accessor("waitingFor")
    ListCraftingInventory insaneae$getWaitingFor();

    @Accessor("timeTracker")
    ElapsedTimeTracker insaneae$getTimeTracker();
}
