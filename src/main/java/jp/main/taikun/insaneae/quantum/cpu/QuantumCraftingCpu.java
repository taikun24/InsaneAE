package jp.main.taikun.insaneae.quantum.cpu;

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
import net.minecraft.core.HolderLookup;
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

/**
 * Quantum CPU に内蔵されるクラフト CPU —<b>容量を切り分けて何本も同時に受ける</b>。
 *
 * <h2>なぜ切り分けるのか</h2>
 * <p>AE2 のクラフト CPU は<b>1 台につきジョブ 1 本</b>で、実行中は「使用中」として
 * CPU 選択から外れる。容量が 64K 程度なら妥当だが、Quantum CPU は上位階層の
 * クラフトストレージを何個も挿せるので、そのままだと<b>1000K 挿してあっても
 * 100K のクラフトを 1 本始めた瞬間に残り 900K が丸ごと遊ぶ</b>。</p>
 *
 * <p>そこで AE2 のクラスタを 1 個に固定せず、<b>発注のたびに区画
 * ({@link QuantumCpuPartition}) を切り出す</b>。</p>
 * <ol>
 *   <li>普段は<b>空き区画が 1 つ</b>だけあり、<b>残量の全部</b>を容量として名乗る。</li>
 *   <li>そこへ発注が入ると、その区画は<b>そのジョブが要求したバイト数ちょうど</b>に縮んで
 *       実行に入り、<b>余りを持った新しい空き区画</b>が現れる。</li>
 *   <li>ジョブが終わった区画は畳まれ、容量が空き区画へ戻る。</li>
 * </ol>
 *
 * <p>区画は AE2 から見ると<b>普通のクラフト CPU がもう 1 台ある</b>のと同じなので、
 * CPU 選択・毎 tick の実行・クラフト端末の一覧は AE2 の経路をそのまま通る
 * ({@code CraftingServiceQuantumCpuMixin} が名簿へ区画を全部足す)。</p>
 *
 * <h2>スレッドの分け方</h2>
 * <p>協調処理スレッドは<b>実行中のジョブで均等に分ける</b>。区画を増やしても
 * 合計の処理能力は変わらない = 分割は「容量を遊ばせない」ためのものであって、
 * <b>速度を増やす裏技にはならない</b>。</p>
 *
 * <p>同時に受けられる本数の上限は設定 ({@link InsaneAEConfig#quantumCpuMaxJobs()}) で、
 * 1 にすると分割前とまったく同じ「1 台 1 ジョブ」に戻る。</p>
 */
public class QuantumCraftingCpu {

    private final QuantumCpuBlockEntity owner;

    /**
     * 区画。<b>空き区画は高々 1 つ</b>で、残りは発注済み。
     *
     * <p>畳んだ区画の番号は使い回す ({@link #nextId}) ので、CPU 一覧に出る番号は
     * 使っているぶんだけの小さい数に収まる。</p>
     */
    private final List<QuantumCpuPartition> partitions = new ArrayList<>();

    /** 内部スロットの中身から数えた合計バイト数 (正本)。 */
    private BigInteger totalStorage = BigInteger.ZERO;

    /** 内部スロットの中身から数えた合計スレッド数。 */
    private long totalThreads;

    public QuantumCraftingCpu(QuantumCpuBlockEntity owner) {
        this.owner = owner;
    }

    // ------------------------------------------------------------ 区画

    /**
     * CPU 一覧に載せる区画。
     *
     * <p>呼ばれるたびに割り当てを整え直すので、<b>ここが名簿と実態の同期点</b>になる。</p>
     */
    public List<QuantumCpuPartition> partitions() {
        redistribute();
        List<QuantumCpuPartition> formed = new ArrayList<>(partitions.size());
        for (QuantumCpuPartition partition : partitions) {
            // 発注済みの区画は容量が 0 でも名簿に残す (外すとジョブが tick されなくなる)。
            if (partition.isTaken() || partition.allotment().signum() > 0) {
                formed.add(partition);
            }
        }
        return formed;
    }

    /**
     * 空き区画のクラスタ。
     *
     * <p>「この Quantum CPU の CPU」を 1 つ指したい場面 (ゲームテストなど) 用。
     * 実行中のジョブはここには居ない — 発注を受けた区画は別に切り出されている。</p>
     */
    public CraftingCPUCluster cluster() {
        redistribute();
        QuantumCpuPartition free = freePartition();
        if (free == null) {
            // 上限まで埋まっているときは、容量 0 の区画を 1 つ作って返す
            // (容量 0 かつ未発注なので名簿には載らない)。
            free = newPartition();
        }
        return free.cluster();
    }

    /** 空いている区画。上限まで発注済みなら null。 */
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
     * この CPU が成立しているか。
     *
     * <p>ストレージが 1 バイトも無ければ CPU として名乗らない (AE2 のクラフト CPU も
     * クラフトストレージが 1 個も無ければ組み上がらない)。協調処理ユニットだけを
     * 挿しても CPU にはならない、ということでもある。</p>
     */
    public boolean isFormed() {
        return totalStorage.signum() > 0;
    }

    /**
     * 区画が発注を受けた。<b>容量をそのジョブのぶんへ縮め、余りを空き区画へ回す。</b>
     *
     * <p>呼び元は {@code CraftingCpuClusterOwnerMixin} ({@code submitJob} の成功時)。</p>
     */
    void onJobSubmitted(QuantumCpuPartition partition, BigInteger bytes) {
        if (partition.isTaken()) {
            return;
        }
        partition.markTaken();
        // 要求量が名乗っていた容量を超えることはない (AE2 が容量で選んでいる) が、
        // 念のため丸めておく。ここを超えて予約すると残量が負になる。
        BigInteger reserved = bytes.signum() > 0
                ? bytes.min(partition.allotment())
                : BigInteger.ZERO;
        partition.setAllotment(reserved);
        redistribute();
        notifyGrid();
        owner.saveChanges();
    }

    /**
     * 終わった区画を畳む。{@link QuantumCpuBlockEntity#serverTick()} から毎 tick。
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
     * <p>空き区画は<b>残量の全部</b>を名乗る。これが「実行中でも残りぶんは発注できる」の本体。</p>
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
        // ユニットを抜かれて合計が予約を下回ることがある。残量は 0 で止める。
        BigInteger free = totalStorage.subtract(reserved).max(BigInteger.ZERO);

        QuantumCpuPartition idle = freePartition();
        boolean wantIdle = free.signum() > 0 && taken < maxJobs();
        if (wantIdle && idle == null) {
            idle = newPartition();
        } else if (!wantIdle && idle != null && idle.clusterIfPresent() == null) {
            // まだ一度もクラスタを作っていない空き区画だけ畳む。
            // 作った後のものは中身が残っている可能性があるので触らない。
            partitions.remove(idle);
            idle = null;
        }
        if (idle != null) {
            idle.setAllotment(wantIdle ? free : BigInteger.ZERO);
        }

        // スレッドは実行中のジョブで均等割り。空き区画は「自分が受けたらこうなる」値を出す。
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

    /** 番号の空きを拾って区画を作る。 */
    private QuantumCpuPartition newPartition() {
        QuantumCpuPartition partition = new QuantumCpuPartition(this, nextId());
        partitions.add(partition);
        return partition;
    }

    /** まだ使われていない一番小さい番号。 */
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

    /** 同時に受けられるジョブの本数。 */
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
        // (古いワールドではストレージ枠に協調処理ユニットが残っていることもある)。
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
                // 容量は long を超えうるので BigInteger のまま数える
                // (8E を数個入れただけで long の上限に届く)。
                storage = storage.add(exactBytes(type).multiply(BigInteger.valueOf(count)));
                int perUnit = type.getAcceleratorThreads();
                if (perUnit > 0) {
                    // long なので何個入れても溢れない。int へ落とすのは AE2 へ渡す直前だけ。
                    threads += (long) perUnit * count;
                }
            }
        }

        boolean changed = !storage.equals(totalStorage) || threads != totalThreads;
        totalStorage = storage;
        totalThreads = threads;
        if (changed) {
            redistribute();
            // CPU 一覧を組み直させる。これを出さないと、
            // 挿した瞬間には CPU が現れず次の構成変更まで反映されない。
            notifyGrid();
        }
    }

    /** グリッドに「CPU の顔ぶれが変わった」と伝える。 */
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

    /** そのアイテムがクラフトユニットなら、その性能。違えば null。 */
    @Nullable
    public static ICraftingUnitType unitTypeOf(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return null;
        }
        Block block = blockItem.getBlock();
        return block instanceof AbstractCraftingUnitBlock<?> unit ? unit.type : null;
    }

    /** 容量を持つクラフトストレージか (ストレージ枠に入れてよいか)。 */
    public static boolean isStorageUnit(ItemStack stack) {
        ICraftingUnitType type = unitTypeOf(stack);
        return type != null && exactBytes(type).signum() > 0;
    }

    /** 協調処理ユニットか (協調処理枠に入れてよいか)。 */
    public static boolean isAcceleratorUnit(ItemStack stack) {
        ICraftingUnitType type = unitTypeOf(stack);
        return type != null && type.getAcceleratorThreads() > 0;
    }

    /**
     * その階層 1 個ぶんの正確なバイト数。
     *
     * <p>InsaneAE の上位階層は long では表せない ({@code 8E} は 2^63) ので、
     * BigInteger の正本を持つ型ならそちらを使う → {@link ExactCraftingUnitType}。</p>
     */
    private static BigInteger exactBytes(ICraftingUnitType type) {
        if (type instanceof ExactCraftingUnitType exact) {
            return exact.exactStorageBytes();
        }
        long bytes = type.getStorageBytes();
        return bytes > 0L ? BigInteger.valueOf(bytes) : BigInteger.ZERO;
    }

    // ------------------------------------------------------------ 集計値の公開

    /** 挿してあるクラフトストレージの合計。 */
    public BigInteger totalStorage() {
        return totalStorage;
    }

    /** 挿してある協調処理ユニットの合計スレッド数。 */
    public long totalThreads() {
        return totalThreads;
    }

    /** いま発注を受けている区画の数 (= 同時に走っているジョブの本数)。 */
    public int runningJobs() {
        int running = 0;
        for (QuantumCpuPartition partition : partitions) {
            if (partition.isTaken()) {
                running++;
            }
        }
        return running;
    }

    /** まだ発注に使えるバイト数。 */
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

    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (QuantumCpuPartition partition : partitions) {
            if (partition.clusterIfPresent() == null) {
                // 一度も使われていない空き区画。保存すべき中身が無い。
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt(NBT_ID, partition.id());
            entry.putBoolean(NBT_TAKEN, partition.isTaken());
            entry.putByteArray(NBT_BYTES, partition.allotment().toByteArray());
            CompoundTag cpu = new CompoundTag();
            partition.writeToNBT(cpu, registries);
            entry.put(NBT_CPU, cpu);
            list.add(entry);
        }
        if (!list.isEmpty()) {
            data.put(NBT_PARTITIONS, list);
        }
    }

    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        partitions.clear();
        if (!data.contains(NBT_PARTITIONS, Tag.TAG_LIST)) {
            // 区画を切る前のワールド。CPU 1 台ぶんのジョブがそのまま入っている。
            // 予約量は記録されていないので 0 で復元する (そのぶん残量が多めに見えるが、
            // 次にそのジョブが終わった時点で帳尻が合う)。
            QuantumCpuPartition partition = newPartition();
            partition.readFromNBT(data, registries);
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
            partition.readFromNBT(entry.getCompound(NBT_CPU), registries);
        }
    }

    /**
     * ブロックが壊れた・ネットワークから外れたときの後始末。
     *
     * <p>実行中のジョブを全部取り消して、CPU に溜まっている中間物をネットワークへ返す
     * (AE2 のクラフト CPU を壊したときと同じ)。</p>
     */
    public void cancelJob() {
        for (QuantumCpuPartition partition : partitions) {
            partition.cancelJob();
        }
    }

    /** CPU 名の表示を更新する (ブロック名が変わったとき)。 */
    public void updateName() {
        for (QuantumCpuPartition partition : partitions) {
            partition.pushTotals();
        }
    }

    /** ネットワークへ CPU 名簿の作り直しを促す。ノードの出入りで呼ぶ。 */
    public void refresh() {
        notifyGrid();
    }
}
