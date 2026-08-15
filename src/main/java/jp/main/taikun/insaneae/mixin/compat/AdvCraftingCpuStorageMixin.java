package jp.main.taikun.insaneae.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Advanced AE のクラフト CPU クラスタで、<b>クラフトストレージ容量が long から溢れない</b>ようにする。
 *
 * <p>{@link jp.main.taikun.insaneae.mixin.CraftingCPUClusterMixin} と同じ問題。
 * Advanced AE は AE2 のクラスタを使わず {@code AdvCraftingCPUCluster} という自前の複製を持ち、
 * 容量の集計を<b>素の long の足し算・掛け算</b>でやっている:</p>
 *
 * <pre>
 * addBlockEntity:              storage += be.getStorageBytes();      // ladd
 * recalculateRemainingStorage: total = storage * storageMultiplier;  // lmul
 * </pre>
 *
 * <p>InsaneAE のクラフトストレージは 1G 階層から上を担当しているので、
 * <b>数個積んだだけで 922京 (long の上限) を越える</b>。越えると値が<b>負に折り返し</b>、
 * 「割り当てストレージが負のクラスタ」ができあがる。
 * 掛け算のほうは倍率が乗るぶん、もっと手前で溢れる。</p>
 *
 * <h2>表に出かた</h2>
 * <p>例外もログも出ない。容量の表示がおかしくなるだけでなく、
 * 容量を見て判断する側 (ACO の BigInteger ホスト、ジョブの受け入れ判定) が
 * <b>「容量が足りない」あるいは「壊れた値」として静かに諦める</b>。
 * 使う側からは「大きいストレージを積んだときだけクラフトが通らない」に見える。</p>
 *
 * <h2>直し方</h2>
 * <p>足す側・掛ける側の値を<b>その場で頭打ちにする</b>。
 * 足す前に「あと何バイト足せるか」で刻み、掛ける前に「何倍までなら溢れないか」で刻む。
 * こうすると溢れる代わりに {@code Long.MAX_VALUE} で飽和し、
 * 元の計算式にも実行フローにも手を入れずに済む。</p>
 *
 * <p>922京バイトを超えるぶんは表現できないが、<b>負の容量よりはるかにましで</b>、
 * AE2 側のクラスタでこちらが既にやっていることと揃う。</p>
 *
 * <p>{@link Pseudo} 付きなので Advanced AE が入っていなければ黙って読み飛ばされる。
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
     * <p>{@code getStorageBytes()} は {@code > 0} の判定と足し算の 2 か所で呼ばれるが、
     * どちらも同じ値になるので問題ない。飽和しきって 0 を返したときは
     * <b>元の判定がそのまま足し算を飛ばす</b>ので、二重に効く心配もない。</p>
     */
    @Redirect(method = "addBlockEntity",
            at = @At(value = "INVOKE",
                    target = "Lnet/pedroksl/advanced_ae/common/entities/AdvCraftingBlockEntity;"
                            + "getStorageBytes()J"),
            require = 0)
    private long insaneae$saturateStorageBytes(@Coerce Object blockEntity) {
        jp.main.taikun.insaneae.compat.AaeCompatCounters.storageSaturations++;
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
     * 「0 より大きいか」の判定と掛け算の 2 か所で読む。刻んだ値も 1 以上なので、
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
        // storage * multiplier が Long.MAX_VALUE を越えない最大の倍率。
        return Math.min(multiplier, Long.MAX_VALUE / storage);
    }

    /** 相手のクラスをコンパイル時に参照しないための反射呼び出し。 */
    @Unique
    private static long insaneae$storageBytes(Object blockEntity) {
        try {
            return (long) blockEntity.getClass().getMethod("getStorageBytes").invoke(blockEntity);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            // 読めないときは足させない (負の容量を作るよりまし)。
            return 0L;
        }
    }
}
