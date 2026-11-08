package jp.main.taikun.insaneae.quantum.cpu;

import appeng.api.stacks.KeyCounter;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.GridCraftingCpuChange;
import appeng.block.crafting.AbstractCraftingUnitBlock;
import appeng.block.crafting.ICraftingUnitType;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import jp.main.taikun.insaneae.config.InsaneAEConfig;
import jp.main.taikun.insaneae.crafting.ExactCraftingUnitType;
import jp.main.taikun.insaneae.quantum.QuantumCpuBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
//? if >=1.21 {
import net.minecraft.core.HolderLookup;
//?}

/**
 * Quantum CPUに内蔵されるクラフトCPU。容量を切り分けて何本も同時に受ける。
 *
 * <h2>なぜ切り分けるのか</h2>
 * <p>AE2のクラフトCPUは1台につきジョブ1本で、実行中は「使用中」として
 * CPU選択から外れる。容量が64K程度なら妥当だが、Quantum CPUは上位階層の
 * クラフトストレージを何個も挿せるので、そのままだと1000K挿してあっても
 * 100Kのクラフトを1本始めた時点で残り900Kが丸ごと使われなくなる。</p>
 *
 * <p>そこでAE2のクラスタを1個に固定せず、発注のたびに区画
 * ({@link QuantumCpuPartition}) を切り出す。</p>
 * <ol>
 *   <li>普段は空き区画が1つだけあり、残量の全部を容量として名乗る。</li>
 *   <li>そこへ発注が入ると、その区画はそのジョブが要求したバイト数ちょうどに縮んで
 *       実行に入り、余りを持った新しい空き区画が現れる。</li>
 *   <li>ジョブが終わった区画は畳まれ、容量が空き区画へ戻る。</li>
 * </ol>
 *
 * <p>区画はAE2から見ると普通のクラフトCPUがもう1台あるのと同じなので、
 * CPU選択・毎tickの実行・クラフト端末の一覧はAE2の経路をそのまま通る
 * {@code CraftingServiceQuantumCpuMixin}が名簿へ区画を全部足す。</p>
 *
 * <h2>スレッドの分け方</h2>
 * <p>協調処理スレッドは実行中のジョブで均等に分ける。区画を増やしても
 * 合計の処理能力は変わらない。分割は「容量を余らせない」ためのものであって、
 * 速度を増やす裏技にはならない。</p>
 *
 * <p>同時に受けられる本数の上限は設定 ({@link InsaneAEConfig#quantumCpuMaxJobs()}) で、
 * 1にすると分割前とまったく同じ「1台1ジョブ」に戻る。</p>
 */
public class QuantumCraftingCpu {

    private final QuantumCpuBlockEntity owner;

    /**
     * 区画。空き区画は高々1つで、残りは発注済み。
     *
     * <p>畳んだ区画の番号は使い回す ({@link #nextId}) ので、CPU一覧に出る番号は
     * 使っているぶんだけの小さい数に収まる。</p>
     */
    private final List<QuantumCpuPartition> partitions = new ArrayList<>();

    /** 内部スロットの中身から数えた合計バイト数。 */
    private BigInteger totalStorage = BigInteger.ZERO;

    private long totalThreads;

    public QuantumCraftingCpu(QuantumCpuBlockEntity owner) {
        this.owner = owner;
    }

    // ------------------------------------------------------------ 区画

    /**
     * CPU一覧に載せる区画。
     *
     * <p>呼ばれるたびに割り当てを整え直すので、ここが名簿と実態の同期点になる。</p>
     */
    public List<QuantumCpuPartition> partitions() {
        redistribute();
        List<QuantumCpuPartition> formed = new ArrayList<>(partitions.size());
        for (QuantumCpuPartition partition : partitions) {
            // 発注済みの区画は容量が0でも名簿に残す。外すとジョブがtickされなくなる
            if (partition.isTaken() || partition.allotment().signum() > 0) {
                formed.add(partition);
            }
        }
        return formed;
    }

    /**
     * 空き区画のクラスタ。
     *
     * <p>「このQuantum CPUのCPU」を1つ指したい場面、たとえばゲームテストで使う。
     * 実行中のジョブはここには居ない。発注を受けた区画は別に切り出されている。</p>
     */
    public CraftingCPUCluster cluster() {
        redistribute();
        QuantumCpuPartition free = freePartition();
        if (free == null) {
            // 上限まで埋まっているときは、容量0の区画を1つ作って返す
            // 容量0かつ未発注なので名簿には載らない
            free = newPartition();
        }
        return free.cluster();
    }

    /** 空いている区画。上限まで発注済みならnull。 */
    @Nullable
    private QuantumCpuPartition freePartition() {
        for (QuantumCpuPartition partition : partitions) {
            if (!partition.isTaken()) {
                return partition;
            }
        }
        return null;
    }

    /**
     * このCPUが成立しているか。
     *
     * <p>ストレージが1バイトも無ければCPUとして名乗らない。AE2のクラフトCPUも
     * クラフトストレージが1個も無ければ組み上がらない)。協調処理ユニットだけを
     * 挿してもCPUにはならない、ということでもある。</p>
     */
    public boolean isFormed() {
        return totalStorage.signum() > 0;
    }

    /**
     * 区画が発注を受けた。容量をそのジョブのぶんへ縮め、余りを空き区画へ回す。
     *
     * <p>呼び元は{@code CraftingCpuClusterOwnerMixin}で、{@code submitJob}の成功時。</p>
     */
    void onJobSubmitted(QuantumCpuPartition partition, BigInteger bytes) {
        if (partition.isTaken()) {
            return;
        }
        partition.markTaken();
        // 要求量が名乗っていた容量を超えることはない。AE2が容量で選んでいるが、
        // 念のため丸めておく。ここを超えて予約すると残量が負になる
        BigInteger reserved = bytes.signum() > 0 ?
                bytes.min(partition.allotment())
                : BigInteger.ZERO;
        partition.setAllotment(reserved);
        redistribute();
        notifyGrid();
        owner.saveChanges();
    }

    /**
     * 終わった区画を畳む。{@link QuantumCpuBlockEntity#serverTick()} から毎tick。
     *
     * <p>ジョブが終わっていても中間物を戻し切れていない区画は残す
     * ({@link QuantumCpuPartition#isDrained()})。</p>
     */
    public void tick() {
        boolean changed = false;
        for (int i = partitions.size() - 1; i >= 0; i--) {
            QuantumCpuPartition partition = partitions.get(i);
            if (partition.isTaken() && partition.isDrained()) {
                partitions.remove(i);
                changed = true;
            }
        }
        if (changed) {
            redistribute();
            notifyGrid();
            owner.saveChanges();
        }
    }

    /**
     * 容量とスレッドを配り直し、必要なら空き区画を用意する。
     *
     * <p>空き区画は残量の全部を名乗る。これが「実行中でも残りぶんは発注できる」の本体。</p>
     */
    private void redistribute() {
        BigInteger reserved = BigInteger.ZERO;
        int taken = 0;
        for (QuantumCpuPartition partition : partitions) {
            if (partition.isTaken()) {
                reserved = reserved.add(partition.allotment());
                taken++;
            }
        }
        // ユニットを抜かれて合計が予約を下回ることがある。残量は0で止める
        BigInteger free = totalStorage.subtract(reserved).max(BigInteger.ZERO);

        QuantumCpuPartition idle = freePartition();
        boolean wantIdle = free.signum() > 0 && taken < maxJobs();
        if (wantIdle && idle == null) {
            idle = newPartition();
        } else if (!wantIdle && idle != null && idle.clusterIfPresent() == null) {
            // まだ一度もクラスタを作っていない空き区画だけ畳む
            // 作った後のものは中身が残っている可能性があるので触らない
            partitions.remove(idle);
            idle = null;
        }
        if (idle != null) {
            idle.setAllotment(wantIdle ? free : BigInteger.ZERO);
        }

        // スレッドは実行中のジョブで均等割り。空き区画は「自分が受けたらこうなる」値を出す
        int shares = taken == 0 ? 1 : taken;
        long each = totalThreads / shares;
        long extra = totalThreads % shares;
        int nth = 0;
        for (QuantumCpuPartition partition : partitions) {
            if (partition.isTaken()) {
                partition.setThreads(each + (nth++ < extra ? 1 : 0));
            } else {
                partition.setThreads(totalThreads / (taken + 1));
            }
        }
    }

    private QuantumCpuPartition newPartition() {
        QuantumCpuPartition partition = new QuantumCpuPartition(this, nextId());
        partitions.add(partition);
        return partition;
    }

    private int nextId() {
        int id = 1;
        boolean used = true;
        while (used) {
            used = false;
            for (QuantumCpuPartition partition : partitions) {
                if (partition.id() == id) {
                    used = true;
                    id++;
                    break;
                }
            }
        }
        return id;
    }

    private static int maxJobs() {
        return Math.max(1, InsaneAEConfig.quantumCpuMaxJobs());
    }

    // ------------------------------------------------------------ 内部スロット

    /**
     * 内部スロットの中身から合計を数え直し、区画へ反映する。
     *
     * <p>スロットの中身が変わったときと、読み込み直後に呼ぶ。</p>
     */
    public void updateUnits() {
        BigInteger storage = BigInteger.ZERO;
        long threads = 0L;
        // ストレージ枠と協調処理枠は画面上の区分でしかないので、合計は両方から数える
        // 古いワールドではストレージ枠に協調処理ユニットが残っていることもある
        for (InternalInventory inv : List.of(owner.getCraftingUnits(), owner.getAcceleratorUnits())) {
            for (ItemStack stack : inv) {
                if (stack.isEmpty()) {
                    continue;
                }
                ICraftingUnitType type = unitTypeOf(stack);
                if (type == null) {
                    continue;
                }
                int count = stack.getCount();
                // 容量はlongを超えうるのでBigIntegerのまま数える
                // 8Eを数個入れただけでlongの上限に届く
                storage = storage.add(exactBytes(type).multiply(BigInteger.valueOf(count)));
                int perUnit = type.getAcceleratorThreads();
                if (perUnit > 0) {
                    // longなので何個入れても溢れない。intへ落とすのはAE2へ渡す直前だけ
                    threads += (long) perUnit * count;
                }
            }
        }

        boolean changed = !storage.equals(totalStorage) || threads != totalThreads;
        totalStorage = storage;
        totalThreads = threads;
        if (changed) {
            redistribute();
            // CPU一覧を組み直させる。これを出さないと、
            // 挿した時点ではCPUが現れず次の構成変更まで反映されない
            notifyGrid();
        }
    }

    /** グリッドに「CPUの顔ぶれが変わった」と伝える。 */
    private void notifyGrid() {
        IGridNode node = cpuNode();
        if (node == null) {
            return;
        }
        IGrid grid = node.getGrid();
        if (grid != null) {
            grid.postEvent(new GridCraftingCpuChange(node));
        }
    }

    /** そのアイテムがクラフトユニットなら、その性能。違えばnull。 */
    @Nullable
    public static ICraftingUnitType unitTypeOf(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return null;
        }
        Block block = blockItem.getBlock();
        return block instanceof AbstractCraftingUnitBlock<?> unit ? unit.type : null;
    }

    /** 容量を持つクラフトストレージか。ストレージ枠に入れてよいか。 */
    public static boolean isStorageUnit(ItemStack stack) {
        ICraftingUnitType type = unitTypeOf(stack);
        return type != null && exactBytes(type).signum() > 0;
    }

    /** 協調処理ユニットか。協調処理枠に入れてよいか。 */
    public static boolean isAcceleratorUnit(ItemStack stack) {
        ICraftingUnitType type = unitTypeOf(stack);
        return type != null && type.getAcceleratorThreads() > 0;
    }

    /**
     * その階層1個ぶんの正確なバイト数。
     *
     * <p>InsaneAEの上位階層はlongでは表せない。{@code 8E}は2^63なので、
     * 正確な値をBigIntegerで持つ型 ({@link ExactCraftingUnitType}) ならそちらを使う。</p>
     */
    private static BigInteger exactBytes(ICraftingUnitType type) {
        if (type instanceof ExactCraftingUnitType exact) {
            return exact.exactStorageBytes();
        }
        long bytes = type.getStorageBytes();
        return bytes > 0L ? BigInteger.valueOf(bytes) : BigInteger.ZERO;
    }

    // ------------------------------------------------------------ 集計値の公開

    public BigInteger totalStorage() {
        return totalStorage;
    }

    public long totalThreads() {
        return totalThreads;
    }

    /** いま発注を受けている区画の数。= 同時に走っているジョブの本数。 */
    public int runningJobs() {
        int running = 0;
        for (QuantumCpuPartition partition : partitions) {
            if (partition.isTaken()) {
                running++;
            }
        }
        return running;
    }

    public BigInteger freeStorage() {
        redistribute();
        QuantumCpuPartition free = freePartition();
        return free == null ? BigInteger.ZERO : free.allotment();
    }

    // ------------------------------------------------- 区画から見た持ち主

    BlockPos blockPos() {
        return owner.getBlockPos();
    }

    QuantumCpuBlockEntity owner() {
        return owner;
    }

    Component displayName() {
        return owner.getCpuDisplayName();
    }

    IGridNode cpuNode() {
        return owner.getActionableNode();
    }

    Level cpuLevel() {
        return owner.getLevel();
    }

    void cpuMarkDirty() {
        owner.saveChanges();
    }

    // ------------------------------------------------------------ 保存

    private static final String NBT_PARTITIONS = "partitions";
    private static final String NBT_ID = "id";
    private static final String NBT_TAKEN = "taken";
    private static final String NBT_BYTES = "bytes";
    private static final String NBT_CPU = "cpu";

    //? if >=1.21 {
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
    //?} else {
    /*public void writeToNBT(CompoundTag data) {
    *///?}
        ListTag list = new ListTag();
        for (QuantumCpuPartition partition : partitions) {
            if (partition.clusterIfPresent() == null) {
                // 一度も使われていない空き区画。保存すべき中身が無い
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt(NBT_ID, partition.id());
            entry.putBoolean(NBT_TAKEN, partition.isTaken());
            entry.putByteArray(NBT_BYTES, partition.allotment().toByteArray());
            CompoundTag cpu = new CompoundTag();
            //? if >=1.21 {
            partition.writeToNBT(cpu, registries);
            //?} else {
            /*partition.writeToNBT(cpu);
            *///?}
            entry.put(NBT_CPU, cpu);
            list.add(entry);
        }
        if (!list.isEmpty()) {
            data.put(NBT_PARTITIONS, list);
        }
    }

    //? if >=1.21 {
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
    //?} else {
    /*public void readFromNBT(CompoundTag data) {
    *///?}
        partitions.clear();
        if (!data.contains(NBT_PARTITIONS, Tag.TAG_LIST)) {
            // 区画を切る前のワールド。CPU 1台ぶんのジョブがそのまま入っている
            // 予約量は記録されていないので0で復元する。そのぶん残量が多めに見えるが、
            // 次にそのジョブが終わった時点で帳尻が合う)
            QuantumCpuPartition partition = newPartition();
            //? if >=1.21 {
            partition.readFromNBT(data, registries);
            //?} else {
            /*partition.readFromNBT(data);
            *///?}
            if (partition.isBusy()) {
                partition.markTaken();
            }
            return;
        }
        ListTag list = data.getList(NBT_PARTITIONS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            QuantumCpuPartition partition = new QuantumCpuPartition(this, entry.getInt(NBT_ID));
            partitions.add(partition);
            byte[] bytes = entry.getByteArray(NBT_BYTES);
            if (bytes.length > 0) {
                partition.setAllotment(new BigInteger(bytes));
            }
            if (entry.getBoolean(NBT_TAKEN)) {
                partition.markTaken();
            }
            //? if >=1.21 {
            partition.readFromNBT(entry.getCompound(NBT_CPU), registries);
            //?} else {
            /*partition.readFromNBT(entry.getCompound(NBT_CPU));
            *///?}
        }
    }

    /**
     * ブロックが壊れたときの後始末。
     *
     * <p>実行中のジョブを全部取り消して、CPUに溜まっている中間物をネットワークへ返す
     * AE2のクラフトCPUを壊したときと同じ。ネットワークが無い、または満杯で返せなかった分は
     * {@code leftovers} へ移す。CPUに残したままだとブロックと一緒に消える。</p>
     */
    public void cancelJobsAndTakeLeftovers(KeyCounter leftovers) {
        for (QuantumCpuPartition partition : partitions) {
            partition.cancelJobAndTakeLeftovers(leftovers);
        }
    }

    /** CPU名の表示を更新する。ブロック名が変わったとき。 */
    public void updateName() {
        for (QuantumCpuPartition partition : partitions) {
            partition.pushTotals();
        }
    }

    /** ネットワークへCPU名簿の作り直しを促す。ノードの出入りで呼ぶ。 */
    public void refresh() {
        notifyGrid();
    }
}
