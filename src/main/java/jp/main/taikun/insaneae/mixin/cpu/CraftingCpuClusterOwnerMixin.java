package jp.main.taikun.insaneae.mixin.cpu;

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
 * {@code CraftingCPUCluster} が構成ブロックを持たなくても成立するようにする。
 *
 * <p>Quantum CPUの内蔵CPUはAE2の本物の {@code CraftingCPUCluster}を使う。
 * こうすると実行エンジン ({@code CraftingCpuLogic}) も、クラフト端末のCPU一覧も、
 * ジョブ投入もAE2のものがそのまま動き、既存の最適化Mixinも適用されたままになる。
 * ただ1点だけ合わないのが「クラスタは構成ブロックの集まりである」という前提で、
 * AE2は次の3メソッドで代表ブロック ({@code getCore()}) を辿る。</p>
 *
 * <ul>
 *   <li>{@code getNode()} — 代表ブロックのグリッドノード</li>
 *   <li>{@code getLevel()} — 代表ブロックのワールド</li>
 *   <li>{@code markDirty()} — 代表ブロックの {@code saveChanges()}</li>
 * </ul>
 *
 * <p>内蔵CPUの構成ブロックは空なので、そのままだと {@code getCore()} が
 * {@code MachineSource} に入れたQuantum CPUを {@code CraftingBlockEntity} にキャストして落ちる。
 * ここで持ち主に差し替えて、{@code getCore()} をそもそも呼ばせない。</p>
 *
 * <p>持ち主がnullのとき、つまりAE2が自分で組んだクラスタは何もしないので、
 * 本来のクラフトCPUの挙動は変わらない。</p>
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
     * ジョブを受け取ったら持ち主へ伝える。容量の切り分けはそちらの仕事。
     *
     * <p>失敗した結果でもRETURNは通るので、成功したときだけ伝える。
     * 容量が足りないときにInsaneAE自身が返す {@code CPU_TOO_SMALL}
     * ({@code CraftingCPUClusterMixin}) もここで弾かれる。</p>
     *
     * <p>要求量はACOのBigInteger計画ならその正確値を使う。
     * {@code ICraftingPlan#bytes()} はlongなので、桁が溢れた計画では
     * 予約量が実際より小さくなる。つまり、残量を多く見せてしまう。</p>
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
