package jp.main.taikun.insaneae.quantum;

import appeng.crafting.execution.ElapsedTimeTracker;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.api.storage.MEStorage;
import java.util.Optional;
import jp.main.taikun.insaneae.integration.aco.AcoBigIntegerJobRegistry;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import jp.main.taikun.insaneae.mixin.ExecutingCraftingJobAccessor;
import jp.main.taikun.insaneae.mixin.TaskProgressAccessor;

/**
 * AE2 本体のクラフト CPU 用の {@link CraftingJobView}。
 *
 * <p>こちらはコンパイル時に型が分かるので、フィールドへは Mixin のアクセサで直接届く
 * (反射は使わない)。</p>
 */
public final class Ae2CraftingJobView implements CraftingJobView {

    private final ExecutingCraftingJobAccessor job;
    private final appeng.crafting.execution.ExecutingCraftingJob exactJob;
    private final ListCraftingInventory inventory;
    private final CraftingCPUCluster cluster;

    private Ae2CraftingJobView(ExecutingCraftingJob job, ListCraftingInventory inventory,
            CraftingCPUCluster cluster) {
        this.job = (ExecutingCraftingJobAccessor) job;
        this.exactJob = job;
        this.inventory = inventory;
        this.cluster = cluster;
    }

    /** ジョブが無ければ null (まとめ処理は何もしない)。 */
    public static CraftingJobView of(ExecutingCraftingJob job, ListCraftingInventory inventory,
            CraftingCPUCluster cluster) {
        return job == null ? null : new Ae2CraftingJobView(job, inventory, cluster);
    }

    @Override
    public ListCraftingInventory getInventory() {
        return inventory;
    }

    @Override
    public ListCraftingInventory getWaitingFor() {
        return job.insaneae$getWaitingFor();
    }

    @Override
    public ElapsedTimeTracker getTimeTracker() {
        return job.insaneae$getTimeTracker();
    }

    @Override
    public void markDirty() {
        cluster.markDirty();
    }

    @Override
    public Optional<AcoBigIntegerJobRegistry.CraftingCursor> exactTasks() {
        return AcoBigIntegerJobRegistry.find(exactJob)
                .map(exact -> exact.cursor(pattern -> job.insaneae$getTasks().remove(pattern)));
    }

    @Override
    public MEStorage getNetworkStorage() {
        var grid = cluster.getGrid();
        return grid == null ? null : grid.getStorageService().getInventory();
    }

    @Override
    public appeng.api.networking.security.IActionSource getActionSource() {
        return cluster.getSrc();
    }

    @Override
    public TaskCursor tasks() {
        return new MapTaskCursor(job.insaneae$getTasks()) {
            @Override
            protected long read(Object progress) {
                return ((TaskProgressAccessor) progress).insaneae$getValue();
            }

            @Override
            protected void write(Object progress, long value) {
                ((TaskProgressAccessor) progress).insaneae$setValue(value);
            }
        };
    }
}
