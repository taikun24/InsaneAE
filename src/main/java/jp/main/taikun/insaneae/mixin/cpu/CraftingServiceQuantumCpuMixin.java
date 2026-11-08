package jp.main.taikun.insaneae.mixin.cpu;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.crafting.CraftingLink;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import jp.main.taikun.insaneae.quantum.QuantumCpuBlockEntity;
import jp.main.taikun.insaneae.quantum.cpu.QuantumCraftingCpu;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * Quantum CPUの内蔵クラフトCPUを、AE2のCPU名簿へ合流させる。
 *
 * <h2>なぜMixinが要るのか</h2>
 * <p>AE2 19.2には第三者がクラフトCPUを足す口が無い。
 * {@code CraftingService} がCPUを集めるのは</p>
 * <pre>grid.getMachines(CraftingBlockEntity.class) → getCluster()</pre>
 * <p>の1経路だけで、しかも {@code getMachines} はオーナーの厳密なクラスを鍵に引く
 * サブクラスは拾われない。{@code submitJob}も名簿{@code Set<CraftingCPUCluster>}しか見ず、
 * 指定CPUも {@code instanceof CraftingCPUCluster} で弾く。</p>
 *
 * <p>そこで本物の {@code CraftingCPUCluster} を作って名簿に足す形にした
 * ({@link QuantumCraftingCpu})。型が本物なので、この先の
 * ジョブ投入・毎tickのティック・{@code insertIntoCpus}・CPU一覧の描画は
 * AE2の経路をそのまま通る。キャストで落ちる余地が無い。</p>
 *
 * <h2>名簿の作り直しを促す</h2>
 * <p>{@code updateCPUClusters} が走るのは {@code updateList} が立ったtickだけで、
 * AE2はこれを「{@code CraftingBlockEntity} が出入りしたとき」にしか立てない。
 * Quantum CPUの出入りでも立つように {@code addNode} / {@code removeNode} に足している。
 * 内部スロットの中身が変わったときは {@code GridCraftingCpuChange} を投げて立てる
 * ({@code QuantumCraftingCpu#updateUnits})。</p>
 */
@Mixin(value = CraftingService.class, remap = false)
public abstract class CraftingServiceQuantumCpuMixin {

    @Shadow
    @Final
    private Set<CraftingCPUCluster> craftingCPUClusters;

    @Shadow
    @Final
    private IGrid grid;

    @Shadow
    private boolean updateList;

    @Shadow
    public abstract void addLink(CraftingLink link);

    /**
     * AE2が名簿を組み直した直後に、Quantum CPUのぶんを足す。
     *
     * <p>{@code updateCPUClusters} は毎回 {@code clear()} してから組み直すので、
     * ここで足すぶんも毎回入れ直しになる = 取り外しは自動的に反映される。</p>
     *
     * <p>Quantum CPU 1台につき載るのはその時点の区画の数で、
     * 発注のたびに増え、終わると減る ({@code QuantumCraftingCpu})。</p>
     *
     * <p>AE2が構成ブロックのクラスタに対してやっているのと同じく、
     * 復元したリンクを登録し直す。これを飛ばすと、ワールド再読み込み後に
     * 「発注元へ完成を伝える紐」が切れて注文が宙に浮く)。</p>
     */
    @Inject(method = "updateCPUClusters", at = @At("RETURN"))
    private void insaneae$addQuantumCpus(CallbackInfo ci) {
        for (QuantumCpuBlockEntity quantumCpu : grid.getMachines(QuantumCpuBlockEntity.class)) {
            QuantumCraftingCpu cpu = quantumCpu.getCraftingCpu();
            // クラフトストレージが1個も入っていない間はCPUとして名乗らない
            if (!cpu.isFormed()) {
                continue;
            }
            // 内蔵CPUは容量を切り分けて何本も同時に受けるので、名簿へ入れるのは
            // 1台ではなく「いま成立している区画ぜんぶ」
            for (var partition : cpu.partitions()) {
                CraftingCPUCluster cluster = partition.cluster();
                craftingCPUClusters.add(cluster);
                if (cluster.craftingLogic.getLastLink() instanceof CraftingLink link) {
                    addLink(link);
                }
            }
        }
    }

    @Inject(method = "addNode", at = @At("RETURN"))
    private void insaneae$onAddNode(IGridNode node, CompoundTag savedData, CallbackInfo ci) {
        if (node.getOwner() instanceof QuantumCpuBlockEntity) {
            updateList = true;
        }
    }

    @Inject(method = "removeNode", at = @At("RETURN"))
    private void insaneae$onRemoveNode(IGridNode node, CallbackInfo ci) {
        if (node.getOwner() instanceof QuantumCpuBlockEntity) {
            updateList = true;
        }
    }
}
