package jp.main.taikun.insaneae.mixin.aco;

import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.access.ExtendedAePlusBigIntegerCellInventoryAccess;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.math.BigInteger;
import java.util.UUID;
import jp.main.taikun.insaneae.cell.InsaneUltraCreativeCellInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 超強化クリエイティブセルに「BigInteger の在庫を名乗る窓口」を生やす。
 *
 * <h2>なぜ内部のインターフェイスを使っているのか</h2>
 * <p>ACO の {@code BigIntegerStorageSnapshotBridge} は、long を超える在庫を
 * <b>{@code ExtendedAePlusBigIntegerCellInventoryAccess} を実装した MEStorage からしか読まない</b>
 * (1.5.19 で確認)。これは ExtendedAE Plus のセル向けに ACO が Mixin で生やしている
 * <b>内部の access インターフェイス</b>で、公開 API ではない。
 * アドオンのセルが正確な在庫を名乗るための公開フックは今のところ無いので、
 * ひとまずこれに乗っている。<b>ACO 側に公開境界が入ったら乗り換えること。</b></p>
 *
 * <p>名前に ExtendedAePlus と付いているが、ACO 側の判定は {@code instanceof} だけで
 * 相手の Mod を問わない。</p>
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

    @Override
    public Object2ObjectMap<AEKey, BigInteger> aco$getExactStoredAmounts() {
        return new Object2ObjectLinkedOpenHashMap<>(insaneae$self().insaneae$exactAmounts());
    }

    @Override
    public int aco$getExactStoredTypeCount() {
        return insaneae$self().insaneae$exactTypeCount();
    }

    @Override
    public void aco$setExactStoredTypeCount(int typeCount) {
        // クリエイティブなので外から数は変えられない。
    }

    @Override
    public BigInteger aco$getExactStoredTotal() {
        return insaneae$self().insaneae$exactTotal();
    }

    @Override
    public void aco$setExactStoredTotal(BigInteger total) {
        // 同上。
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
