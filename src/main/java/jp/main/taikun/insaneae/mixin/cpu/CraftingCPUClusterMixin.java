package jp.main.taikun.insaneae.mixin.cpu;

import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import jp.main.taikun.insaneae.crafting.IBigCraftingCapacity;
import jp.main.taikun.insaneae.crafting.ICoProcessorCount;
import jp.main.taikun.insaneae.crafting.ExactCraftingUnitType;
import jp.main.taikun.insaneae.integration.aco.AcoBigIntegerPlanBridge;
import jp.main.taikun.insaneae.integration.aco.ExactCraftingCapacityPolicy;
import jp.main.taikun.insaneae.quantum.cpu.QuantumCpuClusterOwner;
import jp.main.taikun.insaneae.quantum.cpu.QuantumOwnedCluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.math.BigInteger;
import java.util.List;
import jp.main.taikun.insaneae.util.SaturatingMath;

/**
 * 16× 以上のクラフト協調処理ユニットをAE2に受け入れさせ、合計スレッド数をlongで数える。
 *
 * <p>AE2の {@code CraftingCPUCluster.addBlockEntity} は
 * 1ブロックあたり16スレッドを超えると例外を投げて弾く:</p>
 * <pre>throw new IllegalArgumentException("Co-processor threads may not exceed 16 per single unit block.");</pre>
 *
 * <p>上限の定数そのものを引き上げるのは {@link CraftingUnitThreadLimitMixin} の担当。
 * こちらはそれで上限を外せなかった場合の受け皿と、longでの数え直しを持つ
 * ({@link ICoProcessorCount})。</p>
 *
 * <p>AE2の {@code accelerator} フィールドは {@code int} の単純加算なので
 * 合計が2^31-1を超えると負値になりCPUが一切クラフトしなくなる。
 * int側は {@code Integer.MAX_VALUE - 1} で飽和させて表示・比較用に残し、
 * 実際の1 tick予算はlong側から計算する ({@code CraftingCpuBudgetMixin})。
 * int側を {@code MAX_VALUE} ぴったりにしないのは、{@code getCoProcessors() + 1} を計算する箇所
 * AE2本体および他Modで再びオーバーフローさせないため。</p>
 *
 * <h2>他Modとの共存</h2>
 * <p>同じ16スレッド上限を外すアドオンは他にもあり、{@code addBlockEntity} は混雑している。
 * 実際に報告のあった構成では、BiggerAE2が同じ{@code @ModifyConstant}で上限1024、
 * ExtendedAE_Plusはこれを、HEADで自前処理して元メソッドをキャンセルし、
 * MAE2は{@code @Redirect}で、AE2OmniCellsはRETURNで{@code accelerator}に加算する。これらが同居する構成があった。</p>
 *
 * <p>そのため、</p>
 * <ol>
 *   <li>注入は全て {@code require = 0}。失敗しても起動は止めない。</li>
 *   <li>スレッド合計を {@code addBlockEntity} で積算しない。
 *       上記のようにメソッドごとキャンセルされる場合があり、そのとき積算は0のままになる。
     *       {@code CraftingCpuBudgetMixin}の1 tick予算がこの値を使うので、
 *       0のままだと「クラッシュしないがクラフトが進まない」状態になる。)
 *       代わりにクラスタが持つブロックから都度数え直す ({@link #insaneae$recount})。</li>
 *   <li>priorityを既定より高くしてある。他Modがこのメソッドを自分のものとしてマージした場合、
     *       相手よりpriorityが高くないと注入自体が例外になる。{@code require = 0}では抑止できない
     *       ため。{@code SpeedBoost#MIXIN_PRIORITY}と同じ理由。</li>
 * </ol>
 *
 * <p>AE2は自前Modなので難読化されておらず {@code remap = false}。</p>
 */
@Mixin(value = CraftingCPUCluster.class, remap = false, priority = 1500)
public abstract class CraftingCPUClusterMixin
        implements ICoProcessorCount, IBigCraftingCapacity {

    @Unique
    private static final Logger INSANEAE$LOGGER = LoggerFactory.getLogger("InsaneAE");

    /** 表示・比較用にAE2のintフィールドへ入れておく上限。 */
    @Unique
    private static final int INT_CAP = Integer.MAX_VALUE - 1;

    /** 正確な容量計算へ使うBigIntegerのゼロ値。 */
    @Unique
    private static final BigInteger STORAGE_ZERO = BigInteger.ZERO;

    @Shadow
    private int accelerator;

    /** AE2本体のクラフトストレージ合計。正確値はinsaneae$exactStorageが保持する。 */
    @Shadow
    private long storage;

    /** このクラスタを構成するブロック。AE2はここへ足すだけで、抜くときはクラスタごと作り直す。 */
    @Shadow
    @Final
    private List<CraftingBlockEntity> blockEntities;

    /** longを超えた後も失わない、クラスタ全体の容量正本。 */
    @Unique
    private BigInteger insaneae$exactStorage = STORAGE_ZERO;

    /** 容量を数えた時点の構成ブロック数。-1は未計算を表す。 */
    @Unique
    private int insaneae$countedStorageBlocks = -1;

    /** 数え直した合計スレッド数 (long)。こちらが1 tick予算の元になる。 */
    @Unique
    private long insaneae$coProcessors;

    /** {@link #insaneae$coProcessors} を数えたときのブロック数。-1は「未計算」。 */
    @Unique
    private int insaneae$countedBlocks = -1;

    @Override
    public long insaneae$coProcessorCount() {
        insaneae$recount();
        return insaneae$coProcessors;
    }

    /**
     * 上限に弾かれたときの受け皿。例外を投げる直前に割り込んでメソッドを打ち切り、
     * 本来AE2がやるはずだった加算 ({@code accelerator += threads}) だけ自分で済ませる。
     *
     * <p>{@link CraftingUnitThreadLimitMixin} が上限を外せていればこの分岐には入らない。
     * 他Modが先に定数を押さえていた場合、たとえばBiggerAE2は上限を1024にするので、
     * 4k以上のユニットは依然として弾かれる) だけここに来る。</p>
     *
     * <p>例外が投げられる時点で「クラスタへの追加・ストレージの加算」は済んでいるので、
     * 残りはスレッド数の加算だけ = {@code addBlockEntity} を丸ごと真似する必要がない。</p>
     */
    @Inject(method = "addBlockEntity",
            at = @At(value = "NEW", target = "java/lang/IllegalArgumentException"),
            cancellable = true, require = 0)
    private void insaneae$acceptOverLimit(CraftingBlockEntity blockEntity, CallbackInfo ci) {
        int threads = blockEntity.getAcceleratorThreads();
        if (threads > 0) {
            accelerator = (int) Math.min((long) accelerator + threads, INT_CAP);
        }
        insaneae$countedBlocks = -1;
        INSANEAE$LOGGER.debug("Accepted a co-processor block with {} threads that AE2's limit rejected.",
                threads);
        ci.cancel();
    }

    /**
     * int側の飽和。ブロックが足されるたびに呼ばれるので、
     * 他ModやAE2本体が {@code getCoProcessors()} を読む前に負値を上限へ戻せる。
     *
     * <p>合計そのものは {@link #insaneae$recount} が持つので、ここでは積算しない
     * このメソッドは他Modにキャンセルされうる。</p>
     */
    @Inject(method = "addBlockEntity", at = @At("RETURN"), require = 0)
    private void insaneae$saturateIntCount(CraftingBlockEntity blockEntity, CallbackInfo ci) {
        // ブロック1個ごとに呼ばれ、直前値が必ずINT_CAP以下なので
        // 1回の加算でのラップは高々1周ぶん
        if (accelerator < 0 || accelerator > INT_CAP) {
            accelerator = INT_CAP;
        }
    }

    /**
     * AE2のlong境界へ返す互換値を、正確なBigInteger容量から作り直す。
     *
     * <p>AE2本体の加算結果が負値へ折り返していても、ここではその値を参照しない。
     * そのため4Eを2個以上接続した場合も、正確な合計を計算したうえでlong側だけを
     * {@code Long.MAX_VALUE}へ飽和できる。</p>
     */
    @Inject(method = "getAvailableStorage", at = @At("HEAD"), cancellable = true, require = 0)
    private void insaneae$exposeSaturatedStorage(CallbackInfoReturnable<Long> cir) {
        // AE2のUI・CPU選択・既存ジョブへは、BigIntegerの正本から作った互換longだけを返す
        cir.setReturnValue(SaturatingMath.toLong(insaneae$recountStorage()));
    }

    /** 構成が確定した直後にも数え直し、元のlongフィールドが負値のまま残らないようにする。 */
    @Inject(method = "addBlockEntity", at = @At("RETURN"), require = 0)
    private void insaneae$refreshExactStorage(CraftingBlockEntity blockEntity, CallbackInfo ci) {
        insaneae$countedStorageBlocks = -1;
        insaneae$recountStorage();
    }

    /** 正確なBigInteger容量を任意の連携Modへ公開する。 */
    @Override
    public BigInteger insaneae$exactStorageCapacity() {
        return insaneae$recountStorage();
    }

    /**
     * ACOの正確なBigInteger計画だけを、InsaneAE自身の正確なCPU容量と比較する。
     *
     * <p>容量内なら戻り値を設定せず、CPU使用中判定・ジョブ作成・リンク登録・
     * セキュリティ処理をAE2本来の{@code submitJob}経路へそのまま任せる。
     * AQE向けの複数ジョブHost台帳は使用しないため、1 CPUにつき1ジョブという
     * InsaneAE本来の制限も変わらない。</p>
     */
    @Inject(method = "submitJob", at = @At("HEAD"), cancellable = true, require = 0)
    private void insaneae$validateExactPlanCapacity(
            IGrid grid,
            ICraftingPlan plan,
            IActionSource source,
            ICraftingRequester requester,
            CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        var exactPlan = AcoBigIntegerPlanBridge.inspect(plan);
        if (exactPlan.isEmpty()) {
            return;
        }

        BigInteger required = exactPlan.get().exactBytes();
        BigInteger capacity = insaneae$recountStorage();
        if (!ExactCraftingCapacityPolicy.fits(required, capacity)) {
            /*
             * AE2の画面に出せるのは「ストレージが不足しています」だけで、どれだけ
             * 足りないのかが分からない。BigIntegerクラフトストレージがACO側のAPI差で
             * long容量へ退避しているのか、単に注文が大きすぎるのかを切り分けられるよう、
             * 正確な必要量と容量をログへ残す。
             */
            INSANEAE$LOGGER.warn(
                    "InsaneAE: refusing a wide crafting job on this CPU;"
                            + " needs {} bytes but the cluster holds {} bytes",
                    ExactCraftingCapacityPolicy.describe(required),
                    ExactCraftingCapacityPolicy.describe(capacity));
            cir.setReturnValue(CraftingSubmitResult.CPU_TOO_SMALL);
        }
    }

    /**
     * クラスタのブロックから合計スレッド数を数え直す。
     *
     * <p>ブロック数が前回と同じなら何もしない。AE2はクラスタにブロックを足すだけで、
     * 構成が変わるときはクラスタごと作り直す。つまり、このインスタンスごと捨てられるので、
     * ブロック数だけ見ていれば取りこぼしはない。</p>
     */
    @Unique
    private void insaneae$recount() {
        // Quantum CPUの内蔵CPUには構成ブロックが無い。合計は内部スロットの持ち主が持っている
        QuantumCpuClusterOwner owner = insaneae$quantumOwner();
        if (owner != null) {
            if (insaneae$countedBlocks == owner.cpuRevision()) {
                return;
            }
            insaneae$coProcessors = owner.cpuCoProcessors();
            insaneae$countedBlocks = owner.cpuRevision();
            accelerator = (int) Math.min(insaneae$coProcessors, INT_CAP);
            return;
        }

        int blocks = blockEntities.size();
        if (blocks == insaneae$countedBlocks) {
            return;
        }

        long threads = 0;
        for (CraftingBlockEntity blockEntity : blockEntities) {
            int perBlock = blockEntity.getAcceleratorThreads();
            if (perBlock > 0) {
                // longなのでブロックが何個あっても溢れない
                threads += perBlock;
            }
        }

        // getAcceleratorThreads()に出てこない加算をするMod、たとえばAE2OmniCellsがいるので、
        // AE2のint側が健全でこちらより大きければそちらを採用する
        if (accelerator > 0 && accelerator > threads) {
            threads = accelerator;
        }

        insaneae$countedBlocks = blocks;
        insaneae$coProcessors = threads;

        // 積算側 (insaneae$saturateIntCount) が他Modにキャンセルされていた場合の保険
        if (accelerator < 0 || accelerator > INT_CAP) {
            accelerator = (int) Math.min(threads, INT_CAP);
        }
    }

    /**
     * 構成ブロックから容量をBigIntegerで一度だけ合計する。
     *
     * <p>AE2のクラスタは構成変更時にブロックを追加し、解体時はクラスタ自体を作り直す。
     * そのためブロック数を世代代わりに使い、毎回のGUI参照でBigInteger加算を繰り返さない。</p>
     */
    @Unique
    private BigInteger insaneae$recountStorage() {
        // 内蔵CPUのぶんは持ち主が数えている。構成ブロックが無いので数えようがない
        QuantumCpuClusterOwner owner = insaneae$quantumOwner();
        if (owner != null) {
            if (insaneae$countedStorageBlocks != owner.cpuRevision()) {
                insaneae$exactStorage = owner.cpuExactStorage();
                insaneae$countedStorageBlocks = owner.cpuRevision();
                storage = SaturatingMath.toLong(insaneae$exactStorage);
            }
            return insaneae$exactStorage;
        }

        int blocks = blockEntities.size();
        if (blocks == insaneae$countedStorageBlocks) {
            return insaneae$exactStorage;
        }

        BigInteger total = STORAGE_ZERO;
        for (CraftingBlockEntity blockEntity : blockEntities) {
            var unitType = blockEntity.getUnitBlock().type;
            // long互換値と正確値が異なるユニットは、型が公開するBigInteger正本を加算する
            if (unitType instanceof ExactCraftingUnitType exactType) {
                total = total.add(exactType.exactStorageBytes());
                continue;
            }
            long perBlock = blockEntity.getStorageBytes();
            // AE2の正のストレージ容量だけを合計し、特殊な負値を容量へ混ぜない
            if (perBlock > 0L) {
                total = total.add(BigInteger.valueOf(perBlock));
            }
        }

        insaneae$exactStorage = total;
        insaneae$countedStorageBlocks = blocks;
        // AE2本体のlongフィールドも常に正の互換値へ整え、他Modの直接参照を壊さない
        storage = SaturatingMath.toLong(total);
        return total;
    }

    /**
     * このクラスタがQuantum CPUの内蔵CPUなら、その持ち主。AE2が組んだクラスタならnull。
     *
     * <p>持ち主は {@code CraftingCpuClusterOwnerMixin} が同じクラスへ生やしたフィールドに入っている。
     * 別Mixinのフィールドは直接見えないので、対象クラスが実装している窓口越しに引く。</p>
     */
    @Unique
    private QuantumCpuClusterOwner insaneae$quantumOwner() {
        return ((QuantumOwnedCluster) (Object) this).insaneae$getOwner();
    }
}
