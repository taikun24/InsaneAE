package jp.main.taikun.insaneae.quantum.cpu;

import appeng.api.networking.IGridNode;
import appeng.api.stacks.KeyCounter;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.MachineSource;
import jp.main.taikun.insaneae.mixin.cpu.CraftingCpuClusterAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import java.math.BigInteger;
import jp.main.taikun.insaneae.util.SaturatingMath;
//? if >=1.21 {
import net.minecraft.core.HolderLookup;
//?}

/**
 * Quantum CPUの中のクラフト1本ぶんの区画。AE2のクラスタを1個持つ。
 *
 * <p>AE2のクラフトCPUは1個につきジョブ1本で、実行中は容量が丸ごと塞がる。
 * Quantum CPUは容量が桁違いなので、それだと「1000Kあるのに100Kのクラフトを
 * 始めた時点で何も発注できない」ことになる。そこでクラスタを1個に固定せず、
 * 発注のたびに区画を切り出す。親は{@link QuantumCraftingCpu}。</p>
 *
 * <p>区画はAE2から見ると普通のクラフトCPUがもう1台あるのと区別が付かない。
 * 名簿にも1台ずつ載り、毎tickの実行もCPU選択もAE2の経路をそのまま通る。</p>
 *
 * <h2>持っている数字</h2>
 * <ul>
 *   <li>{@link #allotment} … この区画の容量。空き区画は残り全部、
 *       発注を受けた区画はそのジョブが要求したバイト数ちょうどに縮む
     *       余りが空き区画へ戻る。これが分割の実体。</li>
 *   <li>{@link #threads} … 親が配った協調処理スレッド数。</li>
 * </ul>
 */
public final class QuantumCpuPartition implements QuantumCpuClusterOwner {

    private final QuantumCraftingCpu pool;

    /** 表示名に付ける番号。1は素の名前のまま。空いた番号は使い回す。 */
    private final int id;

    private CraftingCPUCluster cluster;

    private BigInteger allotment = BigInteger.ZERO;

    private long threads;

    /** 発注を受けたか。受けた区画は容量が固定され、空き区画ではなくなる。 */
    private boolean taken;

    /** 中身が変わるたびに増える版番号。{@code CraftingCPUClusterMixin} の数え直しの目印。 */
    private int revision;

    QuantumCpuPartition(QuantumCraftingCpu pool, int id) {
        this.pool = pool;
        this.id = id;
    }

    int id() {
        return id;
    }

    /**
     * AE2のクラスタ本体。作るのは1回だけ。
     *
     * <p>境界はQuantum CPU自身の1ブロックぶん。クラフト端末の
     * 「どこにあるCPUか」の表示に使われる。</p>
     */
    public CraftingCPUCluster cluster() {
        if (cluster == null) {
            CraftingCPUCluster created = new CraftingCPUCluster(
                    pool.blockPos(), pool.blockPos());
            ((QuantumOwnedCluster) (Object) created).insaneae$setOwner(this);
            // getSrc() はObjects.requireNonNullするので、使われる前に必ず入れておく
            ((CraftingCpuClusterAccessor) (Object) created)
                    .insaneae$setMachineSource(new MachineSource(pool.owner()));
            cluster = created;
            pushTotals();
        }
        return cluster;
    }

    /** まだ作られていなければnull。保存や破棄で「無いのに作る」のを避けるため。 */
    CraftingCPUCluster clusterIfPresent() {
        return cluster;
    }

    // ------------------------------------------------------------ 状態

    /** 発注を受けた区画か。= 容量が固定されているか。 */
    public boolean isTaken() {
        return taken;
    }

    public boolean isBusy() {
        return cluster != null && cluster.isBusy();
    }

    /**
     * 役目を終えて畳んでよいか。
     *
     * <p>ジョブが終わっていても、ネットワークが満杯で中間物を戻せていない間は
     * まだ畳めない。{@code CraftingCpuLogic#tickCraftingLogic}はjobが無くても
     * {@code storeItems()} を試し続けるので、名簿に残しておけばいずれ捌ける)。
     * ここで畳むとその中身ごと消える。</p>
     */
    boolean isDrained() {
        if (cluster == null) {
            return true;
        }
        if (cluster.isBusy()) {
            return false;
        }
        KeyCounter items = new KeyCounter();
        cluster.craftingLogic.getAllItems(items);
        return items.isEmpty();
    }

    public BigInteger allotment() {
        return allotment;
    }

    /** 容量を入れ替える。空き区画は親が毎回ここを更新する。 */
    void setAllotment(BigInteger bytes) {
        if (allotment.equals(bytes)) {
            return;
        }
        allotment = bytes;
        revision++;
        pushTotals();
    }

    void setThreads(long value) {
        if (threads == value) {
            return;
        }
        threads = value;
        revision++;
        pushTotals();
    }

    /** 発注を受けた印を付ける。容量はこの時点の必要量に縮む。 */
    void markTaken() {
        taken = true;
    }

    /** 合計値をAE2のクラスタのフィールドへ書き戻す。 */
    void pushTotals() {
        if (cluster == null) {
            return;
        }
        CraftingCpuClusterAccessor accessor = (CraftingCpuClusterAccessor) (Object) cluster;
        accessor.insaneae$setStorage(SaturatingMath.toLong(allotment));
        accessor.insaneae$setAccelerator((int) Math.min(threads, Integer.MAX_VALUE - 1));
        accessor.insaneae$setName(displayName());
    }

    /** CPU一覧に出す名前。2台目からは番号を付けて見分けられるようにする。 */
    Component displayName() {
        Component base = pool.displayName();
        return id <= 1 ? base : base.copy().append(" #" + id);
    }

    // -------------------------------------------------- QuantumCpuClusterOwner

    @Override
    public BigInteger cpuExactStorage() {
        return allotment;
    }

    @Override
    public long cpuCoProcessors() {
        return threads;
    }

    @Override
    public int cpuRevision() {
        return revision;
    }

    @Override
    public IGridNode cpuNode() {
        return pool.cpuNode();
    }

    @Override
    public Level cpuLevel() {
        return pool.cpuLevel();
    }

    @Override
    public void cpuMarkDirty() {
        pool.cpuMarkDirty();
    }

    /**
     * この区画がジョブを受け取った時点で呼ばれる ({@code CraftingCpuClusterOwnerMixin})。
     *
     * <p>ここで容量をそのジョブのぶんだけに縮める。残りは親が新しい空き区画に回すので、
     * 実行中でも残量ぶんの発注を続けられる。</p>
     */
    @Override
    public void cpuJobSubmitted(BigInteger bytes) {
        pool.onJobSubmitted(this, bytes);
    }

    // ------------------------------------------------------------ 保存

    //? if >=1.21 {
    void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
    //?} else {
    /*void writeToNBT(CompoundTag data) {
    *///?}
        if (cluster != null) {
            //? if >=1.21 {
            cluster.writeToNBT(data, registries);
            //?} else {
            /*cluster.writeToNBT(data);
            *///?}
        }
    }

    //? if >=1.21 {
    void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        cluster().readFromNBT(data, registries);
    //?} else {
    /*void readFromNBT(CompoundTag data) {
        cluster().readFromNBT(data);
    *///?}
    }

    void cancelJobAndTakeLeftovers(KeyCounter leftovers) {
        if (cluster == null) {
            return;
        }
        // cancelはジョブが無いとstoreItemsを呼ばないので、終わったジョブの返し残しも自分で戻す
        // storeItemsはグリッドが無ければ何もしない
        cluster.cancelJob();
        cluster.craftingLogic.storeItems();
        KeyCounter inventory = cluster.craftingLogic.getInventory().list;
        leftovers.addAll(inventory);
        inventory.clear();
        cluster.markDirty();
    }
}
