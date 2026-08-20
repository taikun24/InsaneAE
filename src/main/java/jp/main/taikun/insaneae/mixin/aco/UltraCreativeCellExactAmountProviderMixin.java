package jp.main.taikun.insaneae.mixin.aco;

import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.api.contract.ExactStorageAmountProvider;
import java.math.BigInteger;
import java.util.Map;
import jp.main.taikun.insaneae.cell.InsaneUltraCreativeCellInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 超強化クリエイティブセルが「BigInteger の在庫」を<b>公開契約</b>で名乗る窓口。
 *
 * <p>ACO 1.5.20 で入った {@link ExactStorageAmountProvider} がこれ。
 * {@code BigIntegerStorageSnapshotBridge#captureExactContribution} は
 * <b>内部インターフェイスより先にこちらを見る</b>ので、在庫の読み取りは
 * これだけで足りる (1.5.22 のバイトコードで確認)。</p>
 *
 * <h2>それでも内部インターフェイスを捨てられない理由</h2>
 * <p>公開契約は<b>読み取り専用</b>で、メソッドは {@link #exactStoredAmounts()} 1 本しかない。
 * 在庫を<b>減らす</b>側 ({@code ExactNetworkStorageBridge}) は 1.5.22 でも
 * {@code ExtendedAePlusBigIntegerCellInventoryAccess} しか知らず、
 * 「同じ Map インスタンスを返し続けろ・こちらが in-place で書き換える」という
 * 例の約束もそのまま残っている ({@code exact cell storage map changed between
 * simulation and commit} の文言が jar に健在)。
 * したがって当面は<b>読みは公開契約・書きは内部インターフェイス</b>の二本立てで、
 * {@link UltraCreativeCellExactStorageMixin} と並べて実装する。
 * <b>書き込み側にも公開境界が入ったら、あちらを畳んでこちらに寄せること。</b></p>
 *
 * <h2>別 Mixin に分けてある理由</h2>
 * <p>{@link ExactStorageAmountProvider} は <b>ACO 1.5.20 以降にしか無い</b>。
 * 古い ACO を入れている環境でこれを実装すると、存在しないインターフェイスを
 * 実装したクラスとして検証に失敗し、セルごとロードできなくなる。
 * そこで {@link AcoMixinPlugin} が<b>この Mixin だけ</b>を
 * インターフェイスの実在で追加判定している。</p>
 */
@Mixin(InsaneUltraCreativeCellInventory.class)
public abstract class UltraCreativeCellExactAmountProviderMixin implements ExactStorageAmountProvider {

    /**
     * <b>毎回同じインスタンスを返すこと。</b>読み取りだけならコピーでも足りるが、
     * 同じ Map を書き込み側 ({@link UltraCreativeCellExactStorageMixin}) と共有しており、
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
