package jp.main.taikun.insaneae.mixin.aco;

import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.api.contract.ExactStorageAmountProvider;
import java.math.BigInteger;
import java.util.Map;
import jp.main.taikun.insaneae.cell.InsaneUltraCreativeCellInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 超強化クリエイティブセルが「BigIntegerの在庫」を公開契約で名乗る窓口。
 *
 * <p>ACO 1.5.20で入った {@link ExactStorageAmountProvider} がこれ。
 * {@code BigIntegerStorageSnapshotBridge#captureExactContribution} は
 * 内部インターフェイスより先にこちらを見るので、在庫の読み取りは
 * これだけで足りる。1.5.22のバイトコードで確認した。</p>
 *
 * <h2>それでも内部インターフェイスを捨てられない理由</h2>
 * <p>公開契約は読み取り専用で、メソッドは {@link #exactStoredAmounts()} 1本しかない。
 * 在庫を減らす側 ({@code ExactNetworkStorageBridge}) は1.5.22でも
 * {@code ExtendedAePlusBigIntegerCellInventoryAccess} しか知らず、
 * 「同じMapインスタンスを返し続けろ・こちらがin-placeで書き換える」という
 * 例の約束もそのまま残っている ({@code exact cell storage map changed between
 * simulation and commit} の文言がjarに健在)。
 * したがって当面は読みは公開契約・書きは内部インターフェイスの二本立てで、
 * {@link UltraCreativeCellExactStorageMixin} と並べて実装する。
 * 書き込み側にも公開境界が入ったら、あちらを畳んでこちらに寄せること。</p>
 *
 * <h2>別Mixinに分けてある理由</h2>
 * <p>{@link ExactStorageAmountProvider} はACO 1.5.20以降にしか無い。
 * 古いACOを入れている環境でこれを実装すると、存在しないインターフェイスを
 * 実装したクラスとして検証に失敗し、セルごとロードできなくなる。
 * そこで {@link AcoMixinPlugin} がこのMixinだけを
 * インターフェイスの実在で追加判定している。</p>
 */
@Mixin(InsaneUltraCreativeCellInventory.class)
public abstract class UltraCreativeCellExactAmountProviderMixin implements ExactStorageAmountProvider {

    /**
     * 毎回同じインスタンスを返すこと。読み取りだけならコピーでも足りるが、
     * 同じMapを書き込み側 ({@link UltraCreativeCellExactStorageMixin}) と共有しており、
     * あちらが同一性を要求する。
     */
    @Override
    public Map<AEKey, BigInteger> exactStoredAmounts() {
        return insaneae$providerSelf().insaneae$exactAmounts();
    }

    @Unique
    private InsaneUltraCreativeCellInventory insaneae$providerSelf() {
        return (InsaneUltraCreativeCellInventory) (Object) this;
    }
}
