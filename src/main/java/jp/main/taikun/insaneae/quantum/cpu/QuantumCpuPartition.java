package jp.main.taikun.insaneae.quantum.cpu;

import appeng.api.networking.IGridNode;
import appeng.api.stacks.KeyCounter;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.MachineSource;
import jp.main.taikun.insaneae.mixin.CraftingCpuClusterAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import java.math.BigInteger;
import jp.main.taikun.insaneae.util.SaturatingMath;

/**
 * Quantum CPU の中の<b>クラフト 1 本ぶんの区画</b>。AE2 のクラスタを 1 個持つ。
 *
 * <p>AE2 のクラフト CPU は<b>1 個につきジョブ 1 本</b>で、実行中は容量が丸ごと塞がる。
 * Quantum CPU は容量が桁違いなので、それだと「1000K あるのに 100K のクラフトを
 * 始めた瞬間に何も発注できない」ことになる。そこで<b>クラスタを 1 個に固定せず</b>、
 * 発注のたびに区画を切り出す ({@link QuantumCraftingCpu} が親)。</p>
 *
 * <p>区画は AE2 から見ると<b>普通のクラフト CPU がもう 1 台ある</b>のと区別が付かない。
 * 名簿にも 1 台ずつ載り、毎 tick の実行も CPU 選択も AE2 の経路をそのまま通る。</p>
 *
 * <h2>持っている数字</h2>
 * <ul>
 *   <li>{@link #allotment} … この区画の容量。<b>空き区画は残り全部</b>、
 *       発注を受けた区画は<b>そのジョブが要求したバイト数ちょうど</b>に縮む
 *       (余りが空き区画へ戻る = これが「分割」の本体)。</li>
 *   <li>{@link #threads} … 親が配った協調処理スレッド数。</li>
 * </ul>
 */
public final class QuantumCpuPartition implements QuantumCpuClusterOwner {

    private final QuantumCraftingCpu pool;

    /** 表示名に付ける番号。1 は素の名前のまま。空いた番号は使い回す。 */
    private final int id;

    private CraftingCPUCluster cluster;

    /** この区画が使ってよいバイト数。 */
    private BigInteger allotment = BigInteger.ZERO;

    /** この区画に配られたスレッド数。 */
    private long threads;

    /** 発注を受けたか。受けた区画は容量が固定され、空き区画ではなくなる。 */
    private boolean taken;

    /** 中身が変わるたびに増える版番号 ({@code CraftingCPUClusterMixin} の数え直しの目印)。 */
    private int revision;

    QuantumCpuPartition(QuantumCraftingCpu pool, int id) {
        this.pool = pool;
        this.id = id;
    }

    int id() {
        return id;
    }

    /**
     * AE2 のクラスタ本体。作るのは 1 回だけ。
     *
     * <p>境界は Quantum CPU 自身の 1 ブロックぶん。クラフト端末の
     * 「どこにある CPU か」の表示に使われる。</p>
     */
    public CraftingCPUCluster cluster() {
        if (cluster == null) {
            CraftingCPUCluster created = new CraftingCPUCluster(
                    pool.blockPos(), pool.blockPos());
            ((QuantumOwnedCluster) (Object) created).insaneae$setOwner(this);
            // getSrc() は Objects.requireNonNull するので、使われる前に必ず入れておく。
            ((CraftingCpuClusterAccessor) (Object) created)
                    .insaneae$setMachineSource(new MachineSource(pool.owner()));
            cluster = created;
            pushTotals();
        }
        return cluster;
    }

    /** まだ作られていなければ null。保存や破棄で「無いのに作る」のを避けるため。 */
    CraftingCPUCluster clusterIfPresent() {
        return cluster;
    }

    // ------------------------------------------------------------ 状態

    /** 発注を受けた区画か (= 容量が固定されているか)。 */
    public boolean isTaken() {
        return taken;
    }

    /** ジョブを実行中か。 */
    public boolean isBusy() {
        return cluster != null && cluster.isBusy();
    }

    /**
     * 役目を終えて畳んでよいか。
     *
     * <p>ジョブが終わっていても、<b>ネットワークが満杯で中間物を戻せていない</b>間は
     * まだ畳めない ({@code CraftingCpuLogic#tickCraftingLogic} は job が無くても
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

    /** この区画の容量。 */
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

    /** スレッド数を入れ替える。 */
    void setThreads(long value) {
        if (threads == value) {
            return;
        }
        threads = value;
        revision++;
        pushTotals();
    }

    /** 発注を受けた印を付ける (容量はこの時点の必要量に縮む)。 */
    void markTaken() {
        taken = true;
    }

    /** 合計値を AE2 のクラスタのフィールドへ書き戻す。 */
    void pushTotals() {
        if (cluster == null) {
            return;
        }
        CraftingCpuClusterAccessor accessor = (CraftingCpuClusterAccessor) (Object) cluster;
        accessor.insaneae$setStorage(SaturatingMath.toLong(allotment));
        accessor.insaneae$setAccelerator((int) Math.min(threads, Integer.MAX_VALUE - 1));
        accessor.insaneae$setName(displayName());
    }

    /** CPU 一覧に出す名前。2 台目からは番号を付けて見分けられるようにする。 */
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
     * この区画がジョブを受け取った瞬間に呼ばれる ({@code CraftingCpuClusterOwnerMixin})。
     *
     * <p>ここで<b>容量をそのジョブのぶんだけに縮める</b>。残りは親が新しい空き区画に回すので、
     * 実行中でも残量ぶんの発注を続けられる。</p>
     */
    @Override
    public void cpuJobSubmitted(BigInteger bytes) {
        pool.onJobSubmitted(this, bytes);
    }

    // ------------------------------------------------------------ 保存

    void writeToNBT(CompoundTag data) {
        if (cluster != null) {
            cluster.writeToNBT(data);
        }
    }

    void readFromNBT(CompoundTag data) {
        cluster().readFromNBT(data);
    }

    void cancelJob() {
        if (cluster != null) {
            cluster.cancelJob();
        }
    }
}
