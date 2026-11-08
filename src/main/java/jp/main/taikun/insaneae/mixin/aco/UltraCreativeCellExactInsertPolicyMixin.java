package jp.main.taikun.insaneae.mixin.aco;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.api.vector.ExactVectorStoragePolicy;
import java.math.BigInteger;
import jp.main.taikun.insaneae.cell.InsaneUltraCreativeCellInventory;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 超強化クリエイティブセルを、ACOのexact実行の納品先としても振る舞わせる。
 *
 * <p>ACOの物理実行は完成品を「監査済みexactセル」へしか納品しない。
 * その挿入可否は {@code ExactNetworkStorageBridge#insertionCapacity} が決めていて、
 * ExtendedAE Plusの実セル以外はこの公開ポリシー
 * ({@code api.vector.ExactVectorStoragePolicy}) を実装していない限り挿入0になる。
 * 実装しないままだと、完成品の受け皿がこのセルしか無い盤面では
 * ACOが計画を所有できず、委譲または拒否される。#125を参照し、
 * 「クリエイティブセルに完成品を設定してあるのに受け取らない」という
 * 直感に反する挙動になる。</p>
 *
 * <p>受け入れ規約はAE2側の {@code insert} と同じで、
 * 設定済みの種類だけ、いくらでも受け入れる。量は在庫として名乗っている値
 * ({@link InsaneUltraCreativeCellInventory#exactAmount()}) と同じ桁に抑える
 * ACOの計画エンジンが扱える桁を超える値を返さないため。</p>
 *
 * <p>{@code ExactVectorStoragePolicy} はACO 1.5.23で入ったので、
 * {@link AcoMixinPlugin} がインターフェイスの実在を確かめてから当てる。</p>
 */
@Mixin(InsaneUltraCreativeCellInventory.class)
public abstract class UltraCreativeCellExactInsertPolicyMixin
        implements ExactVectorStoragePolicy {

    @Override
    public BigInteger acoMaximumExactInsert(AEKey key, BigInteger currentAmount) {
        // 設定済みかどうかはAE2側のinsertと同じ規約で判定する
        // 設定済みなら全量受け入れ、それ以外は0
        boolean configured = ((InsaneUltraCreativeCellInventory) (Object) this)
                .insert(key, 1, Actionable.SIMULATE, null) > 0;
        return configured ?
                InsaneUltraCreativeCellInventory.exactAmount()
                : BigInteger.ZERO;
    }
}
