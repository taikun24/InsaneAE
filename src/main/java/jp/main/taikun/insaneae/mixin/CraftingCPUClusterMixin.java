package jp.main.taikun.insaneae.mixin;

import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import jp.main.taikun.insaneae.crafting.ICoProcessorCount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 16× 以上のクラフト協調処理ユニットを AE2 に受け入れさせ、合計スレッド数を <b>long で</b>数える。
 *
 * <p>AE2 の {@code CraftingCPUCluster.addBlockEntity} は
 * <b>1 ブロックあたり 16 スレッドを超えると例外を投げて弾く</b>:</p>
 * <pre>throw new IllegalArgumentException("Co-processor threads may not exceed 16 per single unit block.");</pre>
 *
 * <p>上限の定数そのものを引き上げるのは {@link CraftingUnitThreadLimitMixin} の担当。
 * こちらは<b>それが効かなかった場合の受け皿</b>と、<b>long での数え直し</b>を持つ
 * ({@link ICoProcessorCount})。</p>
 *
 * <p>AE2 の {@code accelerator} フィールドは {@code int} の単純加算なので
 * 合計が 2^31-1 を超えると負値になり CPU が一切クラフトしなくなる。
 * <b>int 側は {@code Integer.MAX_VALUE - 1} で飽和</b>させて表示・比較用に残し、
 * <b>実際の 1 tick 予算は long 側から計算する</b> ({@code CraftingCpuBudgetMixin})。
 * int 側を {@code MAX_VALUE} ぴったりにしないのは、{@code getCoProcessors() + 1} を計算する箇所
 * (AE2 本体および他 Mod) で再びオーバーフローさせないため。</p>
 *
 * <h2>他 Mod との共存 (1.0.1)</h2>
 * <p>同じ 16 スレッド上限を外すアドオンは他にもあり、{@code addBlockEntity} は混雑している。
 * 実際に報告のあった構成では BiggerAE2 (同じ {@code @ModifyConstant}、上限 1024)、
 * ExtendedAE_Plus (HEAD で自前処理して元メソッドを<b>キャンセル</b>)、
 * MAE2 ({@code @Redirect})、AE2OmniCells (RETURN で {@code accelerator} に加算) が同居していた。</p>
 *
 * <p>そのため、</p>
 * <ol>
 *   <li>注入は全て {@code require = 0}。失敗しても起動は止めない。</li>
 *   <li><b>スレッド合計を {@code addBlockEntity} で積算しない</b>。
 *       上記のようにメソッドごとキャンセルされる場合があり、そのとき積算は 0 のままになる。
 *       ({@code CraftingCpuBudgetMixin} の 1 tick 予算がこの値を使うので、
 *       0 のままだと「クラッシュしないがクラフトが進まない」状態になる。)
 *       代わりに<b>クラスタが持つブロックから都度数え直す</b> ({@link #insaneae$recount})。</li>
 *   <li>priority を既定より高くしてある。他 Mod がこのメソッドを自分のものとしてマージした場合、
 *       <b>相手より priority が高くないと注入自体が例外になる</b> ({@code require = 0} では抑止できない)
 *       ため ({@code SpeedBoost#MIXIN_PRIORITY} と同じ理由)。</li>
 * </ol>
 *
 * <p>AE2 は自前 Mod なので難読化されておらず {@code remap = false}。</p>
 */
@Mixin(value = CraftingCPUCluster.class, remap = false, priority = 1500)
public abstract class CraftingCPUClusterMixin implements ICoProcessorCount {

    @Unique
    private static final Logger INSANEAE$LOGGER = LoggerFactory.getLogger("InsaneAE");

    /** 表示・比較用に AE2 の int フィールドへ入れておく上限。 */
    @Unique
    private static final int INT_CAP = Integer.MAX_VALUE - 1;

    @Shadow
    private int accelerator;

    /** このクラスタを構成するブロック。AE2 はここへ足すだけで、抜くときはクラスタごと作り直す。 */
    @Shadow
    @Final
    private List<CraftingBlockEntity> blockEntities;

    /** 数え直した合計スレッド数 (long)。こちらが 1 tick 予算の元になる。 */
    @Unique
    private long insaneae$coProcessors;

    /** {@link #insaneae$coProcessors} を数えたときのブロック数。-1 は「未計算」。 */
    @Unique
    private int insaneae$countedBlocks = -1;

    @Override
    public long insaneae$coProcessorCount() {
        insaneae$recount();
        return insaneae$coProcessors;
    }

    /**
     * 上限に弾かれたときの受け皿。<b>例外を投げる直前</b>に割り込んでメソッドを打ち切り、
     * 本来 AE2 がやるはずだった加算 ({@code accelerator += threads}) だけ自分で済ませる。
     *
     * <p>{@link CraftingUnitThreadLimitMixin} が上限を外せていればこの分岐には入らない。
     * 他 Mod が先に定数を押さえていた場合 (例: BiggerAE2 は上限を 1024 にするので、
     * 4k 以上のユニットは依然として弾かれる) だけここに来る。</p>
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
     * int 側の飽和。ブロックが足されるたびに呼ばれるので、
     * 他 Mod や AE2 本体が {@code getCoProcessors()} を読む前に負値を潰せる。
     *
     * <p>合計そのものは {@link #insaneae$recount} が持つので、ここでは積算しない
     * (このメソッドは他 Mod にキャンセルされうる)。</p>
     */
    @Inject(method = "addBlockEntity", at = @At("RETURN"), require = 0)
    private void insaneae$saturateIntCount(CraftingBlockEntity blockEntity, CallbackInfo ci) {
        // ブロック 1 個ごとに呼ばれ、直前値が必ず INT_CAP 以下なので
        // 1 回の加算でのラップは高々 1 周ぶん。
        if (accelerator < 0 || accelerator > INT_CAP) {
            accelerator = INT_CAP;
        }
    }

    /**
     * クラスタのブロックから合計スレッド数を数え直す。
     *
     * <p>ブロック数が前回と同じなら何もしない。AE2 はクラスタにブロックを<b>足すだけ</b>で、
     * 構成が変わるときはクラスタごと作り直す (= このインスタンスごと捨てられる) ので、
     * ブロック数だけ見ていれば取りこぼしはない。</p>
     */
    @Unique
    private void insaneae$recount() {
        int blocks = blockEntities.size();
        if (blocks == insaneae$countedBlocks) {
            return;
        }

        long threads = 0;
        for (CraftingBlockEntity blockEntity : blockEntities) {
            int perBlock = blockEntity.getAcceleratorThreads();
            if (perBlock > 0) {
                // long なのでブロックが何個あっても溢れない。
                threads += perBlock;
            }
        }

        // getAcceleratorThreads() に出てこない加算をする Mod (例: AE2OmniCells) がいるので、
        // AE2 の int 側が健全でこちらより大きければそちらを採用する。
        if (accelerator > 0 && accelerator > threads) {
            threads = accelerator;
        }

        insaneae$countedBlocks = blocks;
        insaneae$coProcessors = threads;

        // 積算側 (insaneae$saturateIntCount) が他 Mod にキャンセルされていた場合の保険。
        if (accelerator < 0 || accelerator > INT_CAP) {
            accelerator = (int) Math.min(threads, INT_CAP);
        }
    }
}
