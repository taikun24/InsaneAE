package jp.main.taikun.insaneae.mixin;

import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import jp.main.taikun.insaneae.crafting.ICoProcessorCount;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 16× 以上のクラフト協調処理ユニットを AE2 に受け入れさせる。
 *
 * <p>AE2 の {@code CraftingCPUCluster.addBlockEntity} は
 * <b>1 ブロックあたり 16 スレッドを超えると例外を投げて弾く</b>:</p>
 * <pre>throw new IllegalArgumentException("Co-processor threads may not exceed 16 per single unit block.");</pre>
 *
 * <p>その定数を実質無制限に引き上げたうえで、合計スレッド数を <b>long でも数えておく</b>
 * ({@link ICoProcessorCount})。AE2 の {@code accelerator} フィールドは {@code int} の単純加算なので
 * 合計が 2^31-1 を超えると負値になり CPU が一切クラフトしなくなるため、
 * <b>int 側は {@code Integer.MAX_VALUE - 1} で飽和</b>させて表示・比較用に残し、
 * <b>実際の 1 tick 予算は long 側から計算する</b> ({@code CraftingCpuLogicMixin})。</p>
 *
 * <p>int 側を {@code MAX_VALUE} ぴったりにしないのは、
 * {@code getCoProcessors() + 1} を計算する箇所 (AE2 本体および他 Mod) で
 * 再びオーバーフローさせないため。</p>
 *
 * <p>AE2 は自前 Mod なので難読化されておらず {@code remap = false}。</p>
 */
@Mixin(value = CraftingCPUCluster.class, remap = false)
public abstract class CraftingCPUClusterMixin implements ICoProcessorCount {

    /** 表示・比較用に AE2 の int フィールドへ入れておく上限。 */
    @Unique
    private static final int INT_CAP = Integer.MAX_VALUE - 1;

    @Shadow
    private int accelerator;

    /** 同じ合計を long でも持っておく。こちらが 1 tick 予算の元になる。 */
    @Unique
    private long insaneae$coProcessors;

    @Override
    public long insaneae$coProcessorCount() {
        return insaneae$coProcessors;
    }

    @ModifyConstant(method = "addBlockEntity", constant = @Constant(intValue = 16))
    private int insaneae$liftThreadLimit(int original) {
        return Integer.MAX_VALUE;
    }

    @Inject(method = "addBlockEntity", at = @At("RETURN"))
    private void insaneae$countThreads(CraftingBlockEntity blockEntity, CallbackInfo ci) {
        int threads = blockEntity.getAcceleratorThreads();
        if (threads > 0) {
            // long 側は素直に足す (2^63 まではブロック数が現実的に足りないので飽和不要)。
            insaneae$coProcessors += threads;
        }
        // int 側は溢れたら (= 負値になったら) 飽和。ブロック 1 個ごとに呼ばれ、
        // 直前値が必ず INT_CAP 以下なので 1 回の加算でのラップは高々 1 周ぶん。
        if (accelerator < 0 || accelerator > INT_CAP) {
            accelerator = INT_CAP;
        }
    }
}
