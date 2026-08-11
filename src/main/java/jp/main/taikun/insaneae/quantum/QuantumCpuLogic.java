package jp.main.taikun.insaneae.quantum;

import appeng.api.config.LockCraftingMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.crafting.CraftingEvent;
import appeng.menu.AutoCraftingMenu;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import com.mojang.logging.LogUtils;
import jp.main.taikun.insaneae.mixin.PatternProviderLogicAccessor;
import jp.main.taikun.insaneae.provider.InsanePatternProviderLogic;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.math.BigInteger;

/**
 * Quantum CPU の頭脳。パターンプロバイダのロジックをそのまま使いつつ、
 * <b>クラフトテーブル用パターンだけは隣の機械に渡さず自分で組み立てる</b>。
 *
 * <h2>1 tick に何回組めるか</h2>
 * <p>AE2 のクラフト CPU は {@code pushPattern} を 1 クラフトにつき 1 回呼ぶ。
 * ここでは 1 tick あたりの受け入れ回数を {@link QuantumCpuBlockEntity#getCraftsPerTick()} で頭打ちにし、
 * 使い切ったら {@link #isBusy()} が true を返して CPU が他のプロバイダへ回るようにする。</p>
 *
 * <h2>重さ対策</h2>
 * <p>1 回ごとに真面目にレシピを回すと重いので、
 * <b>同じパターンに同じ材料が並んだ場合は最初の 1 回だけ実際に組み立て、
 * 以降は記憶した結果を使い回して個数を足すだけ</b>にしている
 * (記憶は<b>パターンごと</b>。1 枠しか持たないと、同時に走る小レシピが交互に来たときに
 * 毎回無効化されて意味を成さない → {@link #assemblyCache})。
 * さらに完成品はその場でネットワークに入れず {@link QuantumCpuBlockEntity} 側に貯め、
 * <b>1 tick に 1 回だけまとめて挿入</b>する (ME への挿入が一番重いため)。</p>
 *
 * <p>1620 枠に耐えるためのパターン管理 (更新の 1 tick へのまとめ・ハッシュ集合での照合) は
 * 特大パターンプロバイダーと共通 → {@link InsanePatternProviderLogic}。</p>
 *
 * <h2>加工パターンは受け付けない</h2>
 * <p>自分で組めるのはクラフトテーブル系のパターンだけなので、
 * <b>加工 (処理) パターンはパターン枠に入れられない</b> (コンストラクタのフィルタ)。
 * 加工パターンは特大パターンプロバイダーに入れること。
 * この制限より前に入れてあった加工パターンは、取り出せるし従来どおり
 * 隣接インベントリへの押し出しも動く ({@link #pushPattern} のフォールバック) —
 * 中身を消したり動作を止めたりはしない。</p>
 */
public class QuantumCpuLogic extends InsanePatternProviderLogic implements IBulkCraftingProvider {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int GRID_SLOTS = 9;

    /** 組み立て結果を覚えておくパターンの数。同時に走る小レシピの数より多ければよい。 */
    private static final int ASSEMBLY_CACHE_SIZE = 64;

    private final QuantumCpuBlockEntity host;
    private final IManagedGridNode mainNode;

    /** 組み立て用の 3x3。使い回して毎回の割り当てを避ける。 */
    private final CraftingContainer craftingInv =
            new TransientCraftingContainer(new AutoCraftingMenu(), 3, 3);

    /**
     * 実際に組み立てた結果を<b>パターンごとに</b>覚えておく表。
     *
     * <p>以前はここが 1 枠しか無く、直前に組んだパターンとしか照合できなかった。
     * ところが 1 つのクラフトジョブには普通いくつもの小レシピが同時に走っていて、
     * CPU 側はタスクの表を舐めながら順に押し出してくる。<b>別のパターンが交互に来ると
     * 1 枠のキャッシュは毎回無効化される</b>ので、命中率が 0 に落ちて
     * {@link #assembleAndCache} を毎クラフト走らせていた (レシピの実行と
     * {@code fireAutoCraftingEvent} の発火が丸ごと乗る)。</p>
     *
     * <p>上限付きの LRU にしてある。パターンは 1620 枠あるが、<b>同時に走る小レシピは
     * せいぜい数十</b>なので {@link #ASSEMBLY_CACHE_SIZE} で足りる。</p>
     */
    private final Map<IPatternDetails, Assembly> assemblyCache =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<IPatternDetails, Assembly> eldest) {
                    return size() > ASSEMBLY_CACHE_SIZE;
                }
            };

    /**
     * 1 回の組み立ての結果。<b>材料の並びも一緒に覚える</b>のは、同じパターンでも
     * 代替材料が違えば完成品が変わりうるため ({@link #matchesCache} で照合する)。
     */
    private record Assembly(ItemStack[] grid, ItemStack output, List<ItemStack> remainders) {
    }

    /** この tick の残り組み立て回数。 */
    private long remainingCrafts;
    private long budgetTick = Long.MIN_VALUE;

    private boolean warnedAboutFailedAssembly;

    public QuantumCpuLogic(IManagedGridNode mainNode, QuantumCpuBlockEntity host) {
        super(mainNode, host, QuantumCpuBlockEntity.PATTERN_SLOTS);
        this.mainNode = mainNode;
        this.host = host;

        // 加工パターンお断り。自分で組めるパターンだけを受け付ける。
        // AppEngSlot#mayPlace もインベントリの isItemValid を見るので、
        // 画面・Shift クリック・パターン端末・インポートバスのどこから入れても効く。
        // 既に入っているぶんには触らない (NBT の読み込みと取り出しはフィルタを通らない)。
        ((AppEngInternalInventory) getPatternInv()).setFilter(new IAEItemFilter() {
            @Override
            public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
                Level level = host.getLevel();
                return level != null && PatternDetailsHelper.decodePattern(stack, level)
                        instanceof IMolecularAssemblerSupportedPattern;
            }
        });
    }

    // -------------------------------------------------------------- パターン管理

    /** パターン一覧が変わった: レシピ自体が差し替わっている可能性がある
     * (データパックの再読み込みなど) ので、覚えている組み立て結果は捨てる。 */
    @Override
    protected void onPatternsFlushed() {
        assemblyCache.clear();
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        // 加工パターン (処理パターン) は自前では組めないので、
        // 普通のパターンプロバイダとして隣接インベントリへ押し出す。
        if (!(patternDetails instanceof IMolecularAssemblerSupportedPattern pattern)) {
            return super.pushPattern(patternDetails, inputHolder);
        }

        Level level = host.getLevel();
        if (level == null || level.isClientSide()) {
            return false;
        }
        if (!mainNode.isActive() || !hasPattern(patternDetails)) {
            return false;
        }
        if (getCraftingLockedReason() != LockCraftingMode.NONE) {
            return false;
        }
        if (budget() <= 0) {
            return false;
        }

        fillGrid(pattern, inputHolder, Leftovers.RETURN);

        Assembly assembly = resolveAssembly(patternDetails, pattern, level);
        if (assembly == null) {
            // 組めなかった: 材料を握りつぶさずネットワークへ返す。
            // (CPU 側の待ち行列は満たされないのでジョブは止まるが、アイテムは消えない)
            returnGridToNetwork();
            return true;
        }

        remainingCrafts--;
        storeOutputs(assembly, 1);
        ((PatternProviderLogicAccessor) this).insaneae$onPushPatternSuccess(patternDetails);
        return true;
    }

    // ------------------------------------------------------------ まとめ処理

    /**
     * この tick にあとどれだけまとめて組めるか。
     * クラフトテーブル用パターン以外は 0 を返し、AE2 本来の 1 回ずつの経路に任せる。
     */
    @Override
    public long getBulkCapacity(IPatternDetails details) {
        if (!(details instanceof IMolecularAssemblerSupportedPattern)) {
            return 0;
        }
        Level level = host.getLevel();
        if (level == null || level.isClientSide()) {
            return 0;
        }
        if (!mainNode.isActive() || !hasPattern(details)) {
            return 0;
        }
        if (getCraftingLockedReason() != LockCraftingMode.NONE || super.isBusy()) {
            return 0;
        }
        return budget();
    }

    /**
     * {@code times} 回ぶんの材料を受け取り、<b>組み立ては 1 回だけ</b>行って結果を {@code times} 倍する。
     *
     * <p>{@code inputHolder} の扱いに注意。{@link #fillGrid} がグリッドに載せるのは 1 回ぶんだけで、
     * 残りの {@code times - 1} 回ぶんは {@code inputHolder} に残ったままになる。
     * これを<b>消費済みとして捨てられるのは組み立てが成功したときだけ</b>で、
     * 失敗したときは呼び出し側 ({@code QuantumBulkCrafting}) が CPU の在庫へ戻せるよう
     * <b>そのまま残して返す</b>。</p>
     */
    @Override
    public long pushPatternBulk(IPatternDetails details, KeyCounter[] inputHolder, long times) {
        if (!(details instanceof IMolecularAssemblerSupportedPattern pattern) || times <= 0) {
            return 0;
        }
        Level level = host.getLevel();
        if (level == null || budget() < times) {
            return 0;
        }

        KeyCounter[] originalInputs = copyInputHolder(inputHolder);
        fillGrid(pattern, inputHolder, Leftovers.KEEP);

        Assembly assembly = resolveAssembly(details, pattern, level);
        if (assembly == null) {
            // 組立失敗時はpushPatternBulkの外側が材料を戻すため、ここでは
            // inputHolderを呼出し前の状態へ戻し、グリッドを空にする。
            // 一部だけをネットワークへ返すと、外側のRollbackと二重計上になる。
            restoreInputHolder(inputHolder, originalInputs);
            clearCraftingGrid();
            return 0;
        }

        // ここまで来たら times 回ぶんすべてを消費したことになるので、残りは捨ててよい。
        for (KeyCounter counter : inputHolder) {
            counter.reset();
        }

        remainingCrafts -= times;
        storeOutputs(assembly, times);
        ((PatternProviderLogicAccessor) this).insaneae$onPushPatternSuccess(details);
        return times;
    }

    /** 完成品と端材を {@code times} 回ぶん貯める。 */
    private void storeOutputs(Assembly assembly, long times) {
        ItemStack output = assembly.output();
        // 掛け算結果をlongへ戻さない。ここがBigInteger会計へ入る唯一の出力境界。
        BigInteger count = BigInteger.valueOf(times).multiply(BigInteger.valueOf(output.getCount()));
        host.addPendingOutput(AEItemKey.of(output), count);
        for (ItemStack remainder : assembly.remainders()) {
            BigInteger remainderCount = BigInteger.valueOf(times)
                    .multiply(BigInteger.valueOf(remainder.getCount()));
            host.addPendingOutput(AEItemKey.of(remainder), remainderCount);
        }
    }

    /**
     * 加工パターンの押し出し待ちが残っているか、この tick の組み立て回数を使い切っていれば忙しい。
     * CPU 側はここで true を見ると他のプロバイダに回るので、無駄な {@code pushPattern} が減る。
     */
    @Override
    public boolean isBusy() {
        return super.isBusy() || budget() <= 0;
    }

    /** この tick に残っている組み立て回数。tick が変わっていれば補充する。 */
    private long budget() {
        Level level = host.getLevel();
        if (level == null) {
            return 0;
        }
        long now = level.getGameTime();
        if (now != budgetTick) {
            budgetTick = now;
            remainingCrafts = host.getCraftsPerTick();
        }
        return remainingCrafts;
    }

    /** {@link #fillGrid} が使い残した材料をどうするか。 */
    private enum Leftovers {
        /**
         * ネットワークに返して空にする。1 回ずつの経路 ({@link #pushPattern}) 用。
         * ここでは残りが出ること自体が基本無いが、出たら消さずに返す。
         */
        RETURN,
        /**
         * <b>そのまま残す。</b>まとめ処理では残り = まだ組んでいない {@code times - 1} 回ぶんなので、
         * 組み立てが成功して初めて「消費済み」になる。<b>先に捨ててはいけない</b> —
         * 捨ててから組み立てに失敗すると、呼び出し側が戻そうとしても中身が無く、
         * {@code times - 1} 回ぶんが消滅する。
         */
        KEEP
    }

    /**
     * 材料をグリッドに並べる。{@code fillCraftingGrid} は<b>常に 1 回ぶんだけ</b>取り出す。
     */
    private void fillGrid(IMolecularAssemblerSupportedPattern pattern, KeyCounter[] inputHolder,
            Leftovers leftovers) {
        for (int slot = 0; slot < GRID_SLOTS; slot++) {
            craftingInv.setItem(slot, ItemStack.EMPTY);
        }
        pattern.fillCraftingGrid(inputHolder, craftingInv::setItem);

        if (leftovers == Leftovers.KEEP) {
            return;
        }
        for (KeyCounter counter : inputHolder) {
            counter.removeZeros();
            for (var entry : counter) {
                host.addPendingOutput(entry.getKey(), entry.getLongValue());
            }
            counter.reset();
        }
    }

    /**
     * 今グリッドに並んでいる材料に対する組み立て結果を返す。
     * 覚えていればそれを使い、無ければ実際にレシピを回す。組めなければ null。
     */
    private Assembly resolveAssembly(IPatternDetails patternDetails,
            IMolecularAssemblerSupportedPattern pattern, Level level) {
        Assembly cached = assemblyCache.get(patternDetails);
        if (cached != null && matchesGrid(cached)) {
            return cached;
        }
        return assembleAndCache(patternDetails, pattern, level);
    }

    /** 今グリッドに並んでいる材料が、記憶している組み立て結果のものと同じか。 */
    private boolean matchesGrid(Assembly assembly) {
        ItemStack[] grid = assembly.grid();
        for (int slot = 0; slot < GRID_SLOTS; slot++) {
            ItemStack expected = grid[slot];
            ItemStack actual = craftingInv.getItem(slot);
            if (expected.getCount() != actual.getCount() || !ItemStack.isSameItemSameTags(expected, actual)) {
                return false;
            }
        }
        return true;
    }

    /** 実際にレシピを回して結果を記憶する。組めなければ null。 */
    private Assembly assembleAndCache(IPatternDetails patternDetails,
            IMolecularAssemblerSupportedPattern pattern, Level level) {
        // getRemainingItems() は中身を書き換えることがあるので、先に材料の並びを控える。
        ItemStack[] grid = new ItemStack[GRID_SLOTS];
        for (int slot = 0; slot < GRID_SLOTS; slot++) {
            grid[slot] = craftingInv.getItem(slot).copy();
        }

        ItemStack output = pattern.assemble(craftingInv, level);
        if (output.isEmpty()) {
            if (!warnedAboutFailedAssembly) {
                warnedAboutFailedAssembly = true;
                LOGGER.warn("InsaneAE: Quantum CPU could not assemble pattern {} at {}; "
                        + "ingredients are returned to the network.",
                        patternDetails.getDefinition(), host.getBlockPos());
            }
            assemblyCache.remove(patternDetails);
            return null;
        }

        // 1 回ぶんの組み立てとして 1 度だけ発火する。
        // 以降の同一クラフトはまとめて処理するため、クラフト回数ぶんは発火しない。
        CraftingEvent.fireAutoCraftingEvent(level, pattern, output, craftingInv);

        NonNullList<ItemStack> remainders = pattern.getRemainingItems(craftingInv);
        List<ItemStack> kept = new ArrayList<>();
        for (ItemStack remainder : remainders) {
            if (!remainder.isEmpty()) {
                kept.add(remainder.copy());
            }
        }

        Assembly assembly = new Assembly(grid, output.copy(), List.copyOf(kept));
        assemblyCache.put(patternDetails, assembly);
        return assembly;
    }

    /** グリッド上の材料をネットワークに戻す (組み立て失敗時の後始末)。 */
    private void returnGridToNetwork() {
        for (int slot = 0; slot < GRID_SLOTS; slot++) {
            ItemStack stack = craftingInv.getItem(slot);
            if (!stack.isEmpty()) {
                AEKey key = AEItemKey.of(stack);
                if (key != null) {
                    host.addPendingOutput(key, stack.getCount());
                }
                craftingInv.setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    /** まとめ処理失敗時に、fillCraftingGrid前の入力所有量を複製する。 */
    private static KeyCounter[] copyInputHolder(KeyCounter[] inputHolder) {
        KeyCounter[] copy = new KeyCounter[inputHolder.length];
        for (int index = 0; index < inputHolder.length; index++) {
            copy[index] = new KeyCounter();
            copy[index].addAll(inputHolder[index]);
        }
        return copy;
    }

    /** 呼出し前の入力へ戻し、BulkCraftingHook側のRollbackへ所有権を返す。 */
    private static void restoreInputHolder(KeyCounter[] inputHolder, KeyCounter[] originalInputs) {
        for (int index = 0; index < inputHolder.length; index++) {
            inputHolder[index].reset();
            inputHolder[index].addAll(originalInputs[index]);
        }
    }

    /** 組立失敗後に、次のPatternへ残った材料を誤流用しない。 */
    private void clearCraftingGrid() {
        for (int slot = 0; slot < GRID_SLOTS; slot++) {
            craftingInv.setItem(slot, ItemStack.EMPTY);
        }
    }
}
