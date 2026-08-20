package jp.main.taikun.insaneae.mixin.aco;

import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.access.ExtendedAePlusBigIntegerCellInventoryAccess;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.math.BigInteger;
import java.util.UUID;
import jp.main.taikun.insaneae.cell.InsaneUltraCreativeCellInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 超強化クリエイティブセルに「BigInteger の在庫を名乗る窓口」を生やす。
 *
 * <h2>これは<b>書き込み側</b>の担当</h2>
 * <p>在庫を<b>読む</b>のは公開契約
 * ({@link UltraCreativeCellExactAmountProviderMixin}) に移した。ACO 1.5.20 で
 * {@code ExactStorageAmountProvider} が入り、スナップショット側は
 * そちらを先に見るようになったため。</p>
 *
 * <p>残っているのは<b>在庫を減らす側</b>。{@code ExactNetworkStorageBridge} は
 * 1.5.22 でもこの {@code ExtendedAePlusBigIntegerCellInventoryAccess} しか知らず、
 * 公開の代替が無い。これは ExtendedAE Plus のセル向けに ACO が Mixin で生やしている
 * <b>内部の access インターフェイス</b>だが、判定は {@code instanceof} だけなので
 * 相手の Mod は問わない。<b>書き込み側にも公開境界が入ったら、このクラスは畳むこと。</b></p>
 *
 * <p>ACO が無い環境では {@link AcoMixinPlugin} がこの Mixin ごと適用を止める。</p>
 */
@Mixin(InsaneUltraCreativeCellInventory.class)
public abstract class UltraCreativeCellExactStorageMixin
        implements ExtendedAePlusBigIntegerCellInventoryAccess {

    /**
     * この中身インスタンスの識別子。クリエイティブセルは保存する状態を持たないので、
     * <b>要求されたときに 1 つ作って使い回すだけ</b>でよい。
     */
    @Unique
    private UUID insaneae$exactStorageUuid;

    /**
     * <b>毎回同じインスタンスを返すこと。</b>ACO はシミュレーションとコミットで
     * このマップを {@code ==} で突き合わせ、<b>直接書き換えて</b>在庫を減らす。
     * コピーを返すと {@code exact cell storage map changed between simulation and commit} で
     * 取引ごと巻き戻される。
     */
    @Override
    public Object2ObjectMap<AEKey, BigInteger> aco$getExactStoredAmounts() {
        return insaneae$self().insaneae$exactAmounts();
    }

    @Override
    public int aco$getExactStoredTypeCount() {
        return insaneae$self().insaneae$exactTypeCount();
    }

    @Override
    public void aco$setExactStoredTypeCount(int typeCount) {
        // 書かれた値は覚えておく (次の取引で beforeTypes と突き合わされる)。
        insaneae$self().insaneae$setExactTypeCount(typeCount);
    }

    @Override
    public BigInteger aco$getExactStoredTotal() {
        return insaneae$self().insaneae$exactTotal();
    }

    @Override
    public void aco$setExactStoredTotal(BigInteger total) {
        insaneae$self().insaneae$setExactTotal(total);
    }

    @Override
    public void aco$saveExactChanges() {
        // 保存するものは無い (中身はセルワークベンチの設定そのもの)。
    }

    @Override
    public boolean aco$hasExactStorageUuid() {
        return insaneae$exactStorageUuid != null;
    }

    @Override
    public UUID aco$getExactStorageUuid() {
        return insaneae$exactStorageUuid;
    }

    @Override
    public UUID aco$assignExactStorageUuid() {
        if (insaneae$exactStorageUuid == null) {
            insaneae$exactStorageUuid = UUID.randomUUID();
        }
        return insaneae$exactStorageUuid;
    }

    @Unique
    private InsaneUltraCreativeCellInventory insaneae$self() {
        return (InsaneUltraCreativeCellInventory) (Object) this;
    }
}
