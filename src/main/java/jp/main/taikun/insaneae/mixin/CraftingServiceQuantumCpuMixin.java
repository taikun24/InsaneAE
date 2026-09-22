package jp.main.taikun.insaneae.mixin;

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
 * Quantum CPU の内蔵クラフト CPU を、AE2 の CPU 名簿へ合流させる。
 *
 * <h2>なぜ Mixin が要るのか</h2>
 * <p>AE2 19.2 には<b>第三者がクラフト CPU を足す口が無い</b>。
 * {@code CraftingService} が CPU を集めるのは</p>
 * <pre>grid.getMachines(CraftingBlockEntity.class) → getCluster()</pre>
 * <p>の 1 経路だけで、しかも {@code getMachines} は<b>オーナーの厳密なクラス</b>を鍵に引く
 * (サブクラスは拾われない)。{@code submitJob} も名簿 {@code Set<CraftingCPUCluster>} しか見ず、
 * 指定 CPU も {@code instanceof CraftingCPUCluster} で弾く。</p>
 *
 * <p>そこで<b>本物の {@code CraftingCPUCluster} を作って名簿に足す</b>形にした
 * ({@link QuantumCraftingCpu})。型が本物なので、この先の
 * ジョブ投入・毎 tick のティック・{@code insertIntoCpus}・CPU 一覧の描画は
 * <b>AE2 の経路をそのまま通る</b> (キャストで落ちる余地が無い)。</p>
 *
 * <h2>名簿の作り直しを促す</h2>
 * <p>{@code updateCPUClusters} が走るのは {@code updateList} が立った tick だけで、
 * AE2 はこれを「{@code CraftingBlockEntity} が出入りしたとき」にしか立てない。
 * Quantum CPU の出入りでも立つように {@code addNode} / {@code removeNode} に足している。
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
     * AE2 が名簿を組み直した直後に、Quantum CPU のぶんを足す。
     *
     * <p>{@code updateCPUClusters} は毎回 {@code clear()} してから組み直すので、
     * ここで足すぶんも毎回入れ直しになる = 取り外しは自動的に反映される。</p>
     *
     * <p>AE2 が構成ブロックのクラスタに対してやっているのと同じく、
     * <b>復元したリンクを登録し直す</b> (これを飛ばすと、ワールド再読み込み後に
     * 「発注元へ完成を伝える紐」が切れて注文が宙に浮く)。</p>
     */
    @Inject(method = "updateCPUClusters", at = @At("RETURN"))
    private void insaneae$addQuantumCpus(CallbackInfo ci) {
        for (QuantumCpuBlockEntity quantumCpu : grid.getMachines(QuantumCpuBlockEntity.class)) {
            QuantumCraftingCpu cpu = quantumCpu.getCraftingCpu();
            // クラフトストレージが 1 個も入っていない間は CPU として名乗らない。
            if (!cpu.isFormed()) {
                continue;
            }
            CraftingCPUCluster cluster = cpu.cluster();
            craftingCPUClusters.add(cluster);
            if (cluster.craftingLogic.getLastLink() instanceof CraftingLink link) {
                addLink(link);
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
