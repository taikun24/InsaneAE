package jp.main.taikun.insaneae.mixin.compat.advancedae;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;
import jp.main.taikun.insaneae.integration.advancedae.AaeCompatCounters;

/**
 * Advanced AEのクラフトCPUクラスタで、クラフトストレージ容量がlongから溢れないようにする。
 *
 * <p>{@link jp.main.taikun.insaneae.mixin.cpu.CraftingCPUClusterMixin} と同じ問題。
 * Advanced AEはAE2のクラスタを使わず {@code AdvCraftingCPUCluster} という自前の複製を持ち、
 * 容量の集計を素のlongの足し算・掛け算でやっている:</p>
 *
 * <pre>
 * addBlockEntity:              storage += be.getStorageBytes();      // ladd
 * recalculateRemainingStorage: total = storage * storageMultiplier;  // lmul
 * </pre>
 *
 * <p>InsaneAEのクラフトストレージは1G階層から上を担当しているので、
 * 数個積んだだけで922京を越える。longの上限を越えると値が負に折り返し、
 * 「割り当てストレージが負のクラスタ」ができあがる。
 * 掛け算のほうは倍率が乗るぶん、もっと手前で溢れる。</p>
 *
 * <h2>表に出かた</h2>
 * <p>例外もログも出ない。容量の表示がおかしくなるだけでなく、
 * 容量を見て判断する側、つまりACOのBigIntegerホストやジョブの受け入れ判定が
 * 「容量が足りない」あるいは「壊れた値」として静かに諦める。
 * 使う側からは「大きいストレージを積んだときだけクラフトが通らない」に見える。</p>
 *
 * <h2>直し方</h2>
 * <p>足す側・掛ける側の値をその場で頭打ちにする。
 * 足す前に「あと何バイト足せるか」で刻み、掛ける前に「何倍までなら溢れないか」で刻む。
 * こうすると溢れる代わりに {@code Long.MAX_VALUE} で飽和し、
 * 元の計算式にも実行フローにも手を入れずに済む。</p>
 *
 * <p>922京バイトを超えるぶんは表現できないが、負の容量よりはるかにましで、
 * AE2側のクラスタでこちらが既にやっていることと揃う。</p>
 *
 * <p>{@link Pseudo} 付きなのでAdvanced AEが入っていなければ黙って読み飛ばされる。
 * 当たらなかった場合は元の桁あふれに戻るだけ。</p>
 */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPUCluster", remap = false)
public abstract class AdvCraftingCpuStorageMixin {

    @Shadow
    private long storage;

    @Shadow
    private long storageMultiplier;

    /**
     * 足す量を「あと入る量」で刻む。
     *
     * <p>{@code getStorageBytes()} は {@code > 0} の判定と足し算の2か所で呼ばれるが、
     * どちらも同じ値になるので問題ない。飽和しきって0を返したときは
     * 元の判定がそのまま足し算を飛ばすので、二重に効く心配もない。</p>
     */
    @Redirect(method = "addBlockEntity",
            at = @At(value = "INVOKE",
                    target = "Lnet/pedroksl/advanced_ae/common/entities/AdvCraftingBlockEntity;"
                            + "getStorageBytes()J"),
            require = 0)
    private long insaneae$saturateStorageBytes(@Coerce Object blockEntity) {
        AaeCompatCounters.STORAGE_SATURATIONS.incrementAndGet();
        long bytes = insaneae$storageBytes(blockEntity);
        if (bytes <= 0) {
            return bytes;
        }
        long room = Long.MAX_VALUE - storage;
        return Math.min(bytes, Math.max(0, room));
    }

    /**
     * 掛ける倍率を「溢れない倍率」で刻む。
     *
     * <p>{@code recalculateRemainingStorage} は {@code storageMultiplier} を
     * 「0より大きいか」の判定と掛け算の2か所で読む。刻んだ値も1以上なので、
     * 判定の結果は変わらない。</p>
     */
    @Redirect(method = "recalculateRemainingStorage",
            at = @At(value = "FIELD",
                    target = "Lnet/pedroksl/advanced_ae/common/cluster/AdvCraftingCPUCluster;"
                            + "storageMultiplier:J",
                    opcode = org.objectweb.asm.Opcodes.GETFIELD),
            require = 0)
    private long insaneae$saturateStorageMultiplier(@Coerce Object cluster) {
        long multiplier = storageMultiplier;
        if (multiplier <= 1 || storage <= 0) {
            return multiplier;
        }
        // storage * multiplierがLong.MAX_VALUEを越えない最大の倍率
        return Math.min(multiplier, Long.MAX_VALUE / storage);
    }

    /** 相手のクラスをコンパイル時に参照しないための反射呼び出し。 */
    @Unique
    private static long insaneae$storageBytes(Object blockEntity) {
        try {
            return (long) blockEntity.getClass().getMethod("getStorageBytes").invoke(blockEntity);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            // 読めないときは足させない。負の容量を作るよりまし
            return 0L;
        }
    }
}
