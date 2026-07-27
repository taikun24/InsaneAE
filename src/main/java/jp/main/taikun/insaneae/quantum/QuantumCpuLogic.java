package jp.main.taikun.insaneae.quantum;

import appeng.api.config.LockCraftingMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.crafting.CraftingEvent;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.menu.AutoCraftingMenu;
import com.mojang.logging.LogUtils;
import jp.main.taikun.insaneae.mixin.PatternProviderLogicAccessor;
import jp.main.taikun.insaneae.upgrade.SpeedBoost;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
 * 以降は記憶した結果を使い回して個数を足すだけ</b>にしている。
 * さらに完成品はその場でネットワークに入れず {@link QuantumCpuBlockEntity} 側に貯め、
 * <b>1 tick に 1 回だけまとめて挿入</b>する (ME への挿入が一番重いため)。</p>
 */
public class QuantumCpuLogic extends PatternProviderLogic implements IBulkCraftingProvider {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int GRID_SLOTS = 9;

    private final QuantumCpuBlockEntity host;
    private final IManagedGridNode mainNode;

    /** 組み立て用の 3x3。使い回して毎回の割り当てを避ける。 */
    private final CraftingContainer craftingInv =
            new TransientCraftingContainer(new AutoCraftingMenu(), 3, 3);

    /** 直前に実際に組み立てたときのパターンと材料の並び。 */
    private IPatternDetails cachedPattern;
    private final ItemStack[] cachedGrid = new ItemStack[GRID_SLOTS];
    private ItemStack cachedOutput = ItemStack.EMPTY;
    private final List<ItemStack> cachedRemainders = new ArrayList<>();

    /** この tick の残り組み立て回数。 */
    private long remainingCrafts;
    private long budgetTick = Long.MIN_VALUE;

    private boolean warnedAboutFailedAssembly;

    public QuantumCpuLogic(IManagedGridNode mainNode, QuantumCpuBlockEntity host) {
        super(mainNode, host, QuantumCpuBlockEntity.PATTERN_SLOTS);
        this.mainNode = mainNode;
        this.host = host;
        Arrays.fill(cachedGrid, ItemStack.EMPTY);
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
        if (!mainNode.isActive() || !getAvailablePatterns().contains(patternDetails)) {
            return false;
        }
        if (getCraftingLockedReason() != LockCraftingMode.NONE) {
            return false;
        }
        if (budget() <= 0) {
            return false;
        }

        fillGrid(pattern, inputHolder, true);

        if (!matchesCache(patternDetails)) {
            if (!assembleAndCache(patternDetails, pattern, level)) {
                // 組めなかった: 材料を握りつぶさずネットワークへ返す。
                // (CPU 側の待ち行列は満たされないのでジョブは止まるが、アイテムは消えない)
                returnGridToNetwork();
                return true;
            }
        }

        remainingCrafts--;
        storeOutputs(1);
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
        if (!mainNode.isActive() || !getAvailablePatterns().contains(details)) {
            return 0;
        }
        if (getCraftingLockedReason() != LockCraftingMode.NONE || super.isBusy()) {
            return 0;
        }
        return budget();
    }

    /**
     * {@code times} 回ぶんの材料を受け取り、<b>組み立ては 1 回だけ</b>行って結果を {@code times} 倍する。
     * 余った材料 ({@code times - 1} 回ぶん) は消費済みなのでそのまま捨てる。
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

        fillGrid(pattern, inputHolder, false);

        if (!matchesCache(details)) {
            if (!assembleAndCache(details, pattern, level)) {
                returnGridToNetwork();
                return 0;
            }
        }

        remainingCrafts -= times;
        storeOutputs(times);
        ((PatternProviderLogicAccessor) this).insaneae$onPushPatternSuccess(details);
        return times;
    }

    /** 完成品と端材を {@code times} 回ぶん貯める。 */
    private void storeOutputs(long times) {
        host.addPendingOutput(AEItemKey.of(cachedOutput),
                SpeedBoost.saturatingMultiply(times, cachedOutput.getCount()));
        for (ItemStack remainder : cachedRemainders) {
            host.addPendingOutput(AEItemKey.of(remainder),
                    SpeedBoost.saturatingMultiply(times, remainder.getCount()));
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

    /**
     * 材料をグリッドに並べる。{@code fillCraftingGrid} は<b>常に 1 回ぶんだけ</b>取り出す。
     *
     * @param returnLeftovers 残った材料をネットワークに返すか。
     *                        まとめ処理では残り = 消費済みの {@code times - 1} 回ぶんなので返してはいけない。
     */
    private void fillGrid(IMolecularAssemblerSupportedPattern pattern, KeyCounter[] inputHolder,
            boolean returnLeftovers) {
        for (int slot = 0; slot < GRID_SLOTS; slot++) {
            craftingInv.setItem(slot, ItemStack.EMPTY);
        }
        pattern.fillCraftingGrid(inputHolder, craftingInv::setItem);

        for (KeyCounter counter : inputHolder) {
            if (returnLeftovers) {
                // 使い切れなかった材料が残ることは基本無いが、残ったらネットワークに返す (消さない)。
                counter.removeZeros();
                for (var entry : counter) {
                    host.addPendingOutput(entry.getKey(), entry.getLongValue());
                }
            }
            counter.reset();
        }
    }

    /** 今グリッドに並んでいる材料が、記憶している組み立て結果のものと同じか。 */
    private boolean matchesCache(IPatternDetails patternDetails) {
        if (cachedPattern == null || !cachedPattern.equals(patternDetails) || cachedOutput.isEmpty()) {
            return false;
        }
        for (int slot = 0; slot < GRID_SLOTS; slot++) {
            ItemStack expected = cachedGrid[slot];
            ItemStack actual = craftingInv.getItem(slot);
            if (expected.getCount() != actual.getCount() || !ItemStack.isSameItemSameTags(expected, actual)) {
                return false;
            }
        }
        return true;
    }

    /** 実際にレシピを回して結果を記憶する。組めなければ false。 */
    private boolean assembleAndCache(IPatternDetails patternDetails,
            IMolecularAssemblerSupportedPattern pattern, Level level) {
        // getRemainingItems() は中身を書き換えることがあるので、先に材料の並びを控える。
        for (int slot = 0; slot < GRID_SLOTS; slot++) {
            cachedGrid[slot] = craftingInv.getItem(slot).copy();
        }

        ItemStack output = pattern.assemble(craftingInv, level);
        if (output.isEmpty()) {
            if (!warnedAboutFailedAssembly) {
                warnedAboutFailedAssembly = true;
                LOGGER.warn("InsaneAE: Quantum CPU could not assemble pattern {} at {}; "
                        + "ingredients are returned to the network.",
                        patternDetails.getDefinition(), host.getBlockPos());
            }
            cachedPattern = null;
            cachedOutput = ItemStack.EMPTY;
            cachedRemainders.clear();
            return false;
        }

        // 1 回ぶんの組み立てとして 1 度だけ発火する。
        // 以降の同一クラフトはまとめて処理するため、クラフト回数ぶんは発火しない。
        CraftingEvent.fireAutoCraftingEvent(level, pattern, output, craftingInv);

        NonNullList<ItemStack> remainders = pattern.getRemainingItems(craftingInv);
        cachedRemainders.clear();
        for (ItemStack remainder : remainders) {
            if (!remainder.isEmpty()) {
                cachedRemainders.add(remainder.copy());
            }
        }

        cachedOutput = output.copy();
        cachedPattern = patternDetails;
        return true;
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
}
