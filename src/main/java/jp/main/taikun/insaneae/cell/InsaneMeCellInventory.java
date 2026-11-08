package jp.main.taikun.insaneae.cell;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.math.BigInteger;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 超次元MEセルの中身。種類も量も上限が無く、量は BigInteger で持つ。
 *
 * <p>AE2 へは 1 種あたり {@code Long.MAX_VALUE} まで報告する。
 * 強化クリエイティブセルと同じく、同じキーを申告する別のセルとの合計が
 * long の天井を越えて負に折り返さないよう、自分の申告で越える分は足さない
 * ({@link InsaneCreativeCellInventory#getAvailableStacks})。</p>
 *
 * <p>インスタンスはアイテムから作り直されるので、変更のたびに {@link #persist()} で
 * アイテムへ書き戻す。</p>
 */
public class InsaneMeCellInventory implements StorageCell {

    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private final ItemStack stack;
    @Nullable
    private final ISaveProvider host;
    private final Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> amounts;

    public InsaneMeCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
        this.stack = stack;
        this.host = host;
        this.amounts = InsaneMeCellData.read(stack);
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0) {
            return 0;
        }
        if (mode == Actionable.MODULATE) {
            amounts.merge(what, BigInteger.valueOf(amount), BigInteger::add);
            persist();
        }
        // 上限は無い。全部受け取る
        return amount;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        BigInteger have = amounts.get(what);
        if (amount <= 0 || have == null) {
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
            persist();
        }
        return take.longValue();
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (Map.Entry<AEKey, BigInteger> entry : amounts.entrySet()) {
            AEKey key = entry.getKey();
            // ここで AE2 に見せる量は long に押し込む。BigInteger の本当の量は中身の側にある
            long reported = entry.getValue().min(LONG_MAX).longValue();
            long headroom = Long.MAX_VALUE - Math.max(out.get(key), 0L);
            long add = Math.min(reported, headroom);
            if (add > 0) {
                out.add(key, add);
            }
        }
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return amounts.containsKey(what);
    }

    @Override
    public CellState getStatus() {
        return amounts.isEmpty() ? CellState.EMPTY : CellState.NOT_EMPTY;
    }

    @Override
    public double getIdleDrain() {
        return 0;
    }

    @Override
    public boolean canFitInsideCell() {
        return amounts.isEmpty();
    }

    @Override
    public Component getDescription() {
        return stack.getHoverName();
    }

    @Override
    public void persist() {
        InsaneMeCellData.write(stack, amounts);
        if (host != null) {
            host.saveChanges();
        }
    }
}
