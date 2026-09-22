package jp.main.taikun.insaneae.mixin;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import jp.main.taikun.insaneae.integration.aco.AcoBigIntegerPlanBridge;
import jp.main.taikun.insaneae.quantum.cpu.QuantumCpuClusterOwner;
import jp.main.taikun.insaneae.quantum.cpu.QuantumOwnedCluster;
import net.minecraft.world.level.Level;

import java.math.BigInteger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code CraftingCPUCluster} が<b>構成ブロックを持たなくても成立する</b>ようにする。
 *
 * <p>Quantum CPU の内蔵 CPU は AE2 の<b>本物の {@code CraftingCPUCluster}</b> を使う。
 * こうすると実行エンジン ({@code CraftingCpuLogic}) も、クラフト端末の CPU 一覧も、
 * ジョブ投入も AE2 のものがそのまま動き、既存の最適化 Mixin も効いたままになる。
 * ただ 1 点だけ噛み合わないのが「クラスタは構成ブロックの集まりである」という前提で、
 * AE2 は次の 3 メソッドで代表ブロック ({@code getCore()}) を辿る。</p>
 *
 * <ul>
 *   <li>{@code getNode()} — 代表ブロックのグリッドノード</li>
 *   <li>{@code getLevel()} — 代表ブロックのワールド</li>
 *   <li>{@code markDirty()} — 代表ブロックの {@code saveChanges()}</li>
 * </ul>
 *
 * <p>内蔵 CPU の構成ブロックは空なので、そのままだと {@code getCore()} が
 * <b>{@code MachineSource} に入れた Quantum CPU を {@code CraftingBlockEntity} にキャストして落ちる</b>。
 * ここで持ち主に差し替えて、{@code getCore()} を<b>そもそも呼ばせない</b>。</p>
 *
 * <p>持ち主が null のとき (= AE2 が自分で組んだクラスタ) は何もしないので、
 * <b>本来のクラフト CPU の挙動は変わらない</b>。</p>
 */
@Mixin(value = CraftingCPUCluster.class, remap = false)
public abstract class CraftingCpuClusterOwnerMixin implements QuantumOwnedCluster {

    @Unique
    private QuantumCpuClusterOwner insaneae$owner;

    @Override
    public void insaneae$setOwner(QuantumCpuClusterOwner owner) {
        this.insaneae$owner = owner;
    }

    @Override
    public QuantumCpuClusterOwner insaneae$getOwner() {
        return this.insaneae$owner;
    }

    @Inject(method = "getNode", at = @At("HEAD"), cancellable = true)
    private void insaneae$getNode(CallbackInfoReturnable<IGridNode> cir) {
        if (insaneae$owner != null) {
            cir.setReturnValue(insaneae$owner.cpuNode());
        }
    }

    @Inject(method = "getLevel", at = @At("HEAD"), cancellable = true)
    private void insaneae$getLevel(CallbackInfoReturnable<Level> cir) {
        if (insaneae$owner != null) {
            cir.setReturnValue(insaneae$owner.cpuLevel());
        }
    }

    /**
     * ジョブを受け取ったら持ち主へ伝える (容量の切り分けはそちらの仕事)。
     *
     * <p>失敗した結果でも RETURN は通るので、<b>成功したときだけ</b>伝える。
     * 容量が足りないときに InsaneAE 自身が返す {@code CPU_TOO_SMALL}
     * ({@code CraftingCPUClusterMixin}) もここで弾かれる。</p>
     *
     * <p>要求量は ACO の BigInteger 計画ならその正確値を使う。
     * {@code ICraftingPlan#bytes()} は long なので、桁が溢れた計画では
     * 予約量が実際より小さくなる (= 残量を多く見せてしまう)。</p>
     */
    @Inject(method = "submitJob", at = @At("RETURN"))
    private void insaneae$onJobSubmitted(IGrid grid, ICraftingPlan plan, IActionSource src,
            ICraftingRequester requester, CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        if (insaneae$owner == null) {
            return;
        }
        ICraftingSubmitResult result = cir.getReturnValue();
        if (result == null || !result.successful()) {
            return;
        }
        BigInteger bytes = AcoBigIntegerPlanBridge.inspect(plan)
                .map(exact -> exact.exactBytes())
                .orElseGet(() -> BigInteger.valueOf(Math.max(plan.bytes(), 0L)));
        insaneae$owner.cpuJobSubmitted(bytes);
    }

    @Inject(method = "markDirty", at = @At("HEAD"), cancellable = true)
    private void insaneae$markDirty(CallbackInfo ci) {
        if (insaneae$owner != null) {
            insaneae$owner.cpuMarkDirty();
            ci.cancel();
        }
    }
}
