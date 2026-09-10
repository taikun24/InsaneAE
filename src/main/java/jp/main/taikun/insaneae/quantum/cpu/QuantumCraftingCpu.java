package jp.main.taikun.insaneae.quantum.cpu;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.GridCraftingCpuChange;
import appeng.block.crafting.AbstractCraftingUnitBlock;
import appeng.block.crafting.ICraftingUnitType;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.MachineSource;
import jp.main.taikun.insaneae.crafting.ExactCraftingUnitType;
import jp.main.taikun.insaneae.mixin.CraftingCpuClusterAccessor;
import jp.main.taikun.insaneae.quantum.QuantumCpuBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.List;

/**
 * Quantum CPU に内蔵されるクラフト CPU。
 *
 * <p><b>AE2 の {@code CraftingCPUCluster} をそのまま 1 個持つ。</b>自前で CPU を書き直していない
 * ので、実行エンジン ({@code CraftingCpuLogic})・クラフト端末の CPU 一覧・ジョブ投入・
 * 既存の最適化 Mixin が<b>全部そのまま効く</b>。</p>
 *
 * <p>AE2 のクラスタとの唯一の違いは<b>構成ブロックを持たないこと</b>。
 * AE2 は「クラスタ = ワールドに並んだクラフトユニットの集まり」として
 * 容量・スレッド数・代表ノードをブロックから引くが、こちらは
 * <b>内部スロットの中身から直接入れる</b>。その差を吸収しているのが
 * {@link QuantumCpuClusterOwner} と {@code CraftingCpuClusterOwnerMixin}。</p>
 *
 * <h2>AE2 の CPU 一覧に載るまで</h2>
 * <p>{@code CraftingService} は CPU を {@code grid.getMachines(CraftingBlockEntity.class)} からしか
 * 集めない (しかも {@code getMachines} は<b>厳密なクラス</b>でしか引けない) ので、
 * ここで作ったクラスタは {@code CraftingServiceQuantumCpuMixin} が名簿へ足している。</p>
 */
public class QuantumCraftingCpu implements QuantumCpuClusterOwner {

    private final QuantumCpuBlockEntity owner;

    /** AE2 の本物のクラスタ。最初に必要になったときに作る。 */
    @Nullable
    private CraftingCPUCluster cluster;

    /** 内部スロットの中身から数えた合計バイト数 (正本)。 */
    private BigInteger exactStorage = BigInteger.ZERO;

    /** 内部スロットの中身から数えた合計スレッド数。 */
    private long coProcessors;

    /**
     * 中身が変わるたびに増える版番号。
     *
     * <p>{@code CraftingCPUClusterMixin} は「構成ブロック数が変わっていなければ数え直さない」
     * という作りなので、構成ブロックを持たないこちらは代わりにこれを世代の目印にする。</p>
     */
    private int revision;

    public QuantumCraftingCpu(QuantumCpuBlockEntity owner) {
        this.owner = owner;
    }

    /**
     * AE2 のクラスタ本体。
     *
     * <p>作るのは 1 回だけ。境界は Quantum CPU 自身の 1 ブロックぶんで、
     * AE2 の CPU 一覧やクラフト端末の「どこにある CPU か」の表示に使われる。</p>
     */
    public CraftingCPUCluster cluster() {
        if (cluster == null) {
            CraftingCPUCluster created = new CraftingCPUCluster(
                    owner.getBlockPos(), owner.getBlockPos());
            ((QuantumOwnedCluster) (Object) created).insaneae$setOwner(this);
            // getSrc() は Objects.requireNonNull するので、使われる前に必ず入れておく。
            ((CraftingCpuClusterAccessor) (Object) created)
                    .insaneae$setMachineSource(new MachineSource(owner));
            cluster = created;
            pushTotals();
        }
        return cluster;
    }

    /** まだ 1 度も作られていなければ null。保存や破棄で「無いのに作る」のを避けるため。 */
    @Nullable
    public CraftingCPUCluster clusterIfPresent() {
        return cluster;
    }

    /**
     * この CPU が成立しているか。
     *
     * <p>ストレージが 1 バイトも無ければ CPU として名乗らない (AE2 のクラフト CPU も
     * クラフトストレージが 1 個も無ければ組み上がらない)。協調処理ユニットだけを
     * 挿しても CPU にはならない、ということでもある。</p>
     */
    public boolean isFormed() {
        return exactStorage.signum() > 0;
    }

    // ------------------------------------------------------------ 内部スロット

    /**
     * 内部スロットの中身から合計を数え直し、クラスタへ反映する。
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

        boolean changed = !storage.equals(exactStorage) || threads != coProcessors;
        exactStorage = storage;
        coProcessors = threads;
        if (changed) {
            revision++;
            pushTotals();
            // CPU 一覧を組み直させる。これを出さないと、
            // 挿した瞬間には CPU が現れず次の構成変更まで反映されない。
            notifyGrid();
        }
    }

    /** 合計値を AE2 のクラスタのフィールドへ書き戻す。 */
    private void pushTotals() {
        if (cluster == null) {
            return;
        }
        CraftingCpuClusterAccessor accessor = (CraftingCpuClusterAccessor) (Object) cluster;
        accessor.insaneae$setStorage(saturate(exactStorage));
        accessor.insaneae$setAccelerator((int) Math.min(coProcessors, Integer.MAX_VALUE - 1));
        accessor.insaneae$setName(owner.getCpuDisplayName());
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

    private static long saturate(BigInteger value) {
        return value.bitLength() >= 64 ? Long.MAX_VALUE : value.longValueExact();
    }

    // ------------------------------------------------------------ 集計値の公開

    // -------------------------------------------------- QuantumCpuClusterOwner

    /** 合計バイト数 (正本)。{@code CraftingCPUClusterMixin} が容量として使う。 */
    @Override
    public BigInteger cpuExactStorage() {
        return exactStorage;
    }

    /** 合計スレッド数。{@code CraftingCpuBudgetMixin} の 1 tick 予算の元になる。 */
    @Override
    public long cpuCoProcessors() {
        return coProcessors;
    }

    /** 中身の版番号。数え直しのキャッシュ判定に使う。 */
    @Override
    public int cpuRevision() {
        return revision;
    }

    @Override
    public IGridNode cpuNode() {
        return owner.getActionableNode();
    }

    @Override
    public Level cpuLevel() {
        return owner.getLevel();
    }

    @Override
    public void cpuMarkDirty() {
        owner.saveChanges();
    }

    // ------------------------------------------------------------ 保存

    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        if (cluster != null) {
            cluster.writeToNBT(data, registries);
        }
    }

    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        cluster().readFromNBT(data, registries);
    }

    /**
     * ブロックが壊れた・ネットワークから外れたときの後始末。
     *
     * <p>実行中のジョブを取り消して、CPU に溜まっている中間物をネットワークへ返す
     * (AE2 のクラフト CPU を壊したときと同じ)。</p>
     */
    public void cancelJob() {
        if (cluster != null) {
            cluster.cancelJob();
        }
    }

    /** CPU 名の表示を更新する (ブロック名が変わったとき)。 */
    public void updateName() {
        if (cluster != null) {
            ((CraftingCpuClusterAccessor) (Object) cluster).insaneae$setName(owner.getCpuDisplayName());
        }
    }

    /** ネットワークへ CPU 名簿の作り直しを促す。ノードの出入りで呼ぶ。 */
    public void refresh() {
        notifyGrid();
    }
}
