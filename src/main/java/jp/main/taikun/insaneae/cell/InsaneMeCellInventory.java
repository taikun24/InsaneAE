package jp.main.taikun.insaneae.cell;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;
import jp.main.taikun.insaneae.cell.storage.CellContentStore;
import jp.main.taikun.insaneae.integration.AddonIntegrations;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 超次元MEセルの在庫。中身は {@link CellContentStore} の表を直接読み書きする。
 *
 * <p>表は保存データと同じものなので、出し入れのたびにアイテムへ書き戻す必要が無い。
 * 書き出しはワールドの保存時にまとめて行われる。</p>
 *
 * <p>AE2のキーの在庫はlongなので、1種あたり {@code Long.MAX_VALUE} までしか見せない。
 * 同じキーを申告する別のセルとの合計が負に折り返さないよう、
 * 自分の申告で天井を越える分は足さない。</p>
 *
 * <p>保存データに届かない場所、つまりクライアントでは読み取り専用になり、何も見せない。</p>
 */
public class InsaneMeCellInventory implements StorageCell {

    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private final ItemStack stack;
    @Nullable
    private final ISaveProvider host;
    @Nullable
    private final CellContentStore store;
    @Nullable
    private CellContentStore.BigEntry entry;

    public InsaneMeCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
        this.stack = stack;
        this.host = host;
        this.store = CellContentStore.get();
        UUID id = InsaneMeCellData.id(stack);
        this.entry = store != null && id != null ? store.getBig(id) : null;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || store == null || !accepts(what)) {
            return 0;
        }
        if (mode == Actionable.MODULATE) {
            if (entry == null) {
                UUID id = UUID.randomUUID();
                entry = store.createBig(id, stack.getItem());
                InsaneMeCellData.setId(stack, id);
            }
            entry.amounts().merge(what, BigInteger.valueOf(amount), BigInteger::add);
            changed();
        }
        return amount;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || entry == null) {
            return 0;
        }
        Map<AEKey, BigInteger> amounts = entry.amounts();
        BigInteger have = amounts.get(what);
        if (have == null) {
            return 0;
        }
        BigInteger take = have.min(BigInteger.valueOf(amount));
        if (mode == Actionable.MODULATE) {
            BigInteger left = have.subtract(take);
            if (left.signum() == 0) {
                amounts.remove(what);
            } else {
                amounts.put(what, left);
            }
            if (amounts.isEmpty()) {
                forget();
            }
            changed();
        }
        return take.longValue();
    }

    /**
     * 中身の入ったセルは入れさせない。AE2のセルと同じ決まりで、入れ子にすると
     * 中のセルごと復元できなくなる。放射性の化学物質のように専用セルの領分のものも弾く。
     */
    private static boolean accepts(AEKey what) {
        if (what instanceof AEItemKey itemKey) {
            StorageCell inner = StorageCells.getCellInventory(itemKey.toStack(), null);
            if (inner != null && !inner.canFitInsideCell()) {
                return false;
            }
        }
        return AddonIntegrations.active().stream().noneMatch(addon -> addon.rejectsInStorage(what));
    }

    /** 空になったら保存データからもアイテムからも消す。UUIDが残ると新品と重ならない。 */
    private void forget() {
        UUID id = InsaneMeCellData.id(stack);
        if (id != null && store != null) {
            store.removeBig(id);
        }
        InsaneMeCellData.clearId(stack);
        entry = null;
    }

    private void changed() {
        if (store != null) {
            store.setDirty();
        }
        if (host != null) {
            host.saveChanges();
        }
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (entry == null) {
            return;
        }
        for (Map.Entry<AEKey, BigInteger> content : entry.amounts().entrySet()) {
            AEKey key = content.getKey();
            long reported = content.getValue().min(LONG_MAX).longValue();
            long headroom = Long.MAX_VALUE - Math.max(out.get(key), 0L);
            long add = Math.min(reported, headroom);
            if (add > 0) {
                out.add(key, add);
            }
        }
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return entry != null && entry.amounts().containsKey(what);
    }

    @Override
    public CellState getStatus() {
        return InsaneMeCellData.id(stack) == null ? CellState.EMPTY : CellState.NOT_EMPTY;
    }

    @Override
    public double getIdleDrain() {
        return 0;
    }

    @Override
    public boolean canFitInsideCell() {
        return InsaneMeCellData.id(stack) == null;
    }

    @Override
    public Component getDescription() {
        return stack.getHoverName();
    }

    @Override
    public void persist() {
        // 中身は保存データの表そのものなので、書き戻す物が無い
    }
}
