package jp.main.taikun.insaneae.iface;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.StorageHelper;
import appeng.util.ConfigInventory;
import org.jetbrains.annotations.Nullable;

/**
 * 外の機械に見せる超特大インターフェイスのインベントリ。
 *
 * <p>中身は {@code InterfaceLogic} の在庫インベントリそのままだが、<b>設定していない枠</b>
 * に入ってきたぶんは<b>枠を経由せず ME ネットワークへ直接入れる</b>。</p>
 *
 * <h2>なぜ必要か</h2>
 *
 * <p>NeoForge の {@code IItemHandler} は 1 回の受け渡しが int (というより ItemStack) なので
 * 問題にならないが、<b>Mekanism 系の化学物質は 1 回の受け渡しが long</b> になる
 * (Applied Mekanistics の {@code GenericStackChemicalStorage#insertChemical} が
 * {@code ChemicalStack#getAmount()} をそのまま流す)。1 枠の上限は
 * {@link InsaneInterfaceBlockEntity#MAX_PER_SLOT} = 21 億なので、
 * 素通しにすると<b>それを超えるぶんが機械側に押し戻される</b>。
 * ネットワークの在庫は long なので、そちらに直接入れれば桁を落とさずに受け取れる。</p>
 *
 * <h2>設定済みの枠には効かない</h2>
 *
 * <p>設定済みの枠 (= 何をいくつ在庫するか決めてある枠) は「決めた数で頭打ち」が
 * インターフェイス本来の動作なので、そこは AE2 のままにしてある。
 * 溢れたぶんまで飲み込むと、在庫指定が「なんでも吸い込む口」になってしまう。</p>
 *
 * <p>取り出し側は枠の中身だけ。ネットワークの中身をここから抜けるようにすると、
 * 隣にホッパーを置くだけでネットワークが空になってしまう
 * (AE2 が {@code GENERIC_INTERNAL_INV} に在庫インベントリしか出していないのと同じ理由)。</p>
 */
public class InterfaceOverflowInventory implements GenericInternalInventory {

    private final InsaneInterfaceBlockEntity host;

    /**
     * ネットワークへの流し込みが自分に戻ってきたときに止めるための印。
     *
     * <p>このインターフェイスにストレージバスを向けるとネットワーク → バス → ここ →
     * ネットワーク…… と回り得るので、入れ子の呼び出しでは素の在庫インベントリとして振る舞う。</p>
     */
    private boolean insertingIntoNetwork;

    public InterfaceOverflowInventory(InsaneInterfaceBlockEntity host) {
        this.host = host;
    }

    private ConfigInventory storage() {
        return host.getInterfaceLogic().getStorage();
    }

    private ConfigInventory config() {
        return host.getInterfaceLogic().getConfig();
    }

    // ------------------------------------------------------------------ 出し入れ

    @Override
    public long insert(int slot, AEKey what, long amount, Actionable mode) {
        if (insertingIntoNetwork || slot >= config().size() || config().getKey(slot) != null) {
            // 設定済みの枠 (と再入) は AE2 と同じ挙動。
            return storage().insert(slot, what, amount, mode);
        }

        // 未設定の枠はネットワークへ直通。入り切らないぶんだけ枠に置いて、
        // 次の tick に InterfaceLogic が押し出す (ネットワークが落ちていても取りこぼさない)。
        long inserted = insertIntoNetwork(what, amount, mode);
        if (inserted < amount) {
            inserted += storage().insert(slot, what, amount - inserted, mode);
        }
        return inserted;
    }

    private long insertIntoNetwork(AEKey what, long amount, Actionable mode) {
        if (!host.getMainNode().isActive()) {
            return 0;
        }
        IGrid grid = host.getMainNode().getGrid();
        if (grid == null) {
            return 0;
        }
        insertingIntoNetwork = true;
        try {
            return StorageHelper.poweredInsert(grid.getEnergyService(),
                    grid.getStorageService().getInventory(), what, amount,
                    IActionSource.ofMachine(host.getInterfaceLogic()), mode);
        } finally {
            insertingIntoNetwork = false;
        }
    }

    @Override
    public long extract(int slot, AEKey what, long amount, Actionable mode) {
        return storage().extract(slot, what, amount, mode);
    }

    // ------------------------------------------------------- 以下は在庫インベントリへ委譲

    @Override
    public int size() {
        return storage().size();
    }

    @Nullable
    @Override
    public GenericStack getStack(int slot) {
        return storage().getStack(slot);
    }

    @Nullable
    @Override
    public AEKey getKey(int slot) {
        return storage().getKey(slot);
    }

    @Override
    public long getAmount(int slot) {
        return storage().getAmount(slot);
    }

    @Override
    public long getMaxAmount(AEKey what) {
        return storage().getMaxAmount(what);
    }

    @Override
    public long getCapacity(AEKeyType space) {
        return storage().getCapacity(space);
    }

    @Override
    public boolean canInsert() {
        return storage().canInsert();
    }

    @Override
    public boolean canExtract() {
        return storage().canExtract();
    }

    @Override
    public void setStack(int slot, @Nullable GenericStack stack) {
        storage().setStack(slot, stack);
    }

    @Override
    public boolean isSupportedType(AEKeyType type) {
        return storage().isSupportedType(type);
    }

    @Override
    public boolean isAllowedIn(int slot, AEKey what) {
        return storage().isAllowedIn(slot, what);
    }

    @Override
    public void beginBatch() {
        storage().beginBatch();
    }

    @Override
    public void endBatch() {
        storage().endBatch();
    }

    @Override
    public void endBatchSuppressed() {
        storage().endBatchSuppressed();
    }

    @Override
    public void onChange() {
        storage().onChange();
    }
}
