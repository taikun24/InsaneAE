package jp.main.taikun.insaneae.mixin.aco;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.api.vector.ExactVectorStoragePolicy;
import java.math.BigInteger;
import jp.main.taikun.insaneae.cell.InsaneUltraCreativeCellInventory;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 超強化クリエイティブセルを、ACO の exact 実行の<b>納品先</b>としても振る舞わせる。
 *
 * <p>ACO の物理実行は完成品を「監査済み exact セル」へしか納品しない。
 * その挿入可否は {@code ExactNetworkStorageBridge#insertionCapacity} が決めていて、
 * ExtendedAE Plus の実セル以外は<b>この公開ポリシー
 * ({@code api.vector.ExactVectorStoragePolicy}) を実装していない限り挿入 0</b> になる。
 * 実装しないままだと、完成品の受け皿がこのセルしか無い盤面では
 * ACO が計画を所有できず (#125 修正後は委譲/拒否、修正前は無言の永久待機)、
 * 「クリエイティブセルに完成品を設定してあるのに受け取らない」という
 * 直感に反する挙動になる。</p>
 *
 * <p>受け入れ規約は AE2 側の {@code insert} と同じ —
 * <b>設定済みの種類だけ、いくらでも飲み込む</b>。量は在庫として名乗っている値
 * ({@link InsaneUltraCreativeCellInventory#exactAmount()}) と同じ桁に抑える
 * (ACO の計画エンジンが扱える桁を超える値を返さないため)。</p>
 *
 * <p>{@code ExactVectorStoragePolicy} は <b>ACO 1.5.23 で入った</b>ので、
 * {@link AcoMixinPlugin} がインターフェイスの実在を確かめてから当てる。</p>
 */
@Mixin(InsaneUltraCreativeCellInventory.class)
public abstract class UltraCreativeCellExactInsertPolicyMixin
        implements ExactVectorStoragePolicy {

    @Override
    public BigInteger acoMaximumExactInsert(AEKey key, BigInteger currentAmount) {
        // 設定済みかどうかの判定は AE2 側の insert と同じ規約に乗る
        // (設定済み → 全量受け入れ、それ以外 → 0)。
        boolean configured = ((InsaneUltraCreativeCellInventory) (Object) this)
                .insert(key, 1, Actionable.SIMULATE, null) > 0;
        return configured
                ? InsaneUltraCreativeCellInventory.exactAmount()
                : BigInteger.ZERO;
    }
}
