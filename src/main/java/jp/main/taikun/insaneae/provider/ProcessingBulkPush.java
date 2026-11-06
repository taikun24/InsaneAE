package jp.main.taikun.insaneae.provider;

import appeng.api.config.Actionable;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PatternProviderTarget;
import jp.main.taikun.insaneae.config.InsaneAEConfig;
import jp.main.taikun.insaneae.mixin.crafting.PatternProviderLogicAccessor;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 加工パターンを N 回ぶんまとめて隣のインベントリへ押し出す処理。
 *
 * <p>AE2 は 1 クラフトごとに {@code pushPattern} を呼び、そのたびに材料の取り出し・電力・
 * 搬入先の照会をやり直す。1 tick に数千回を超えるとこの固定費が支配的になるので、
 * 「搬入先が受け取れる回数」を先に数え、その回数ぶんの材料を 1 回で入れる。</p>
 *
 * <p>AE2 の {@code pushPattern} と結果が変わる条件ではまとめない (0 を返して 1 回ずつの経路に任せる)。</p>
 * <ul>
 *   <li>隣にクラフト機械 ({@link ICraftingMachine}) がある: AE2 はそちらを優先して 1 回ずつ渡す。</li>
 *   <li>ブロッキングモード: 1 回入れたら搬入先が空くまで止まるのが仕様。</li>
 *   <li>LOCK_UNTIL_PULSE / LOCK_UNTIL_RESULT: 1 回押し出すとロックがかかる。</li>
 *   <li>{@link AEProcessingPattern} 以外: 入力 1 つにつき候補が 1 種類だと保証されているのは
 *       AE2 の加工パターンだけで、他 Mod の独自パターンは押し出し方も独自でありうる。</li>
 * </ul>
 */
final class ProcessingBulkPush {

    /**
     * 1 回の照会で搬入先に尋ねる量の上限。
     * 搬入先の多くは数量を int で扱うので、それを超える量を尋ねない。
     */
    private static final long PROBE_UNITS = Integer.MAX_VALUE;

    /**
     * 搬入先 1 つにつき照会と搬入を繰り返す回数の上限。材料 2 種類なら 1 巡で空きの半分ずつ埋まるので、
     * 8 巡で 99% 以上になる。残りは次の tick に回る。
     */
    private static final int MAX_ROUNDS = 8;

    private ProcessingBulkPush() {
    }

    /** 搬入先 1 つ。 */
    private record Target(Direction side, PatternProviderTarget adapter) {
    }

    /**
     * この tick に、今の搬入先へまとめて押し出せる回数の見積もり。まとめられなければ 0。
     * 材料ごとに別々に尋ねた値なので多めに出ることがあり、実際の回数は {@link #push} が返す。
     */
    static long capacity(InsanePatternProviderLogic logic, IPatternDetails details) {
        if (!(details instanceof AEProcessingPattern)) {
            return 0;
        }
        Map<AEKey, Long> perCraft = perCraftFromPattern(details);
        if (perCraft == null) {
            return 0;
        }
        List<Target> targets = targetsIfEligible(logic, details);
        if (targets == null) {
            return 0;
        }
        long wanted = probeCrafts(perCraft, Long.MAX_VALUE);
        long total = 0;
        for (Target target : targets) {
            total += acceptedCrafts(target.adapter(), perCraft, wanted - total);
            if (total >= wanted) {
                break;
            }
        }
        return total;
    }

    /**
     * 最大 {@code times} 回ぶんの材料を押し出し、押し出した回数を返す。
     *
     * <p>押し出したぶんの材料は {@code inputHolder} から取り除き、残りはそのまま残す
     * (呼び出し側が CPU へ戻す)。</p>
     *
     * <p>搬入先への照会は材料ごとに独立しているので、材料が 2 種類以上あると
     * 同じ空き枠を両方が数えてしまう (チェストなど)。そこで 1 巡ごとに
     * 「照会で分かった回数 ÷ 材料の種類数」だけ入れ、照会し直して埋まるまで繰り返す。
     * 種類ごとに空きの 1/種類数 しか使わないので、枠を共有していても溢れるのは端数の枠ぶんだけで、
     * そのぶんは AE2 と同じく送信待ちに積む。</p>
     */
    static long push(InsanePatternProviderLogic logic, IPatternDetails details,
            KeyCounter[] inputHolder, long times) {
        if (times <= 0 || !(details instanceof AEProcessingPattern)) {
            return 0;
        }
        List<Target> targets = targetsIfEligible(logic, details);
        if (targets == null) {
            return 0;
        }
        Map<AEKey, Long> perCraft = perCraftFromHolder(details, inputHolder, times);
        if (perCraft == null) {
            return 0;
        }
        int kinds = perCraft.size();

        var accessor = (PatternProviderLogicAccessor) logic;
        int start = Math.floorMod(accessor.insaneae$getRoundRobinIndex(), targets.size());

        long left = times;
        int used = 0;
        Direction overflowSide = null;
        for (int i = 0; i < targets.size() && left > 0 && overflowSide == null; i++) {
            Target target = targets.get((start + i) % targets.size());
            used = i + 1;
            for (int round = 0; round < MAX_ROUNDS && left > 0 && overflowSide == null; round++) {
                long crafts = acceptedCrafts(target.adapter(), perCraft, left);
                if (crafts <= 0) {
                    break;
                }
                if (kinds > 1) {
                    crafts = Math.max(1, crafts / kinds);
                }
                for (Map.Entry<AEKey, Long> input : perCraft.entrySet()) {
                    long amount = input.getValue() * crafts;
                    long inserted = target.adapter().insert(input.getKey(), amount, Actionable.MODULATE);
                    if (inserted < amount) {
                        // 端数の枠で入りきらなかった。AE2 と同じく送信待ちに積み、この回で打ち切る。
                        accessor.insaneae$addToSendList(input.getKey(), amount - inserted);
                        overflowSide = target.side();
                    }
                }
                left -= crafts;
            }
        }
        long done = times - left;
        if (done <= 0) {
            return 0;
        }

        for (Map.Entry<AEKey, Long> input : perCraft.entrySet()) {
            removeFromHolder(inputHolder, input.getKey(), input.getValue() * done);
        }
        accessor.insaneae$onPushPatternSuccess(details);
        if (overflowSide != null) {
            accessor.insaneae$setSendDirection(overflowSide);
            accessor.insaneae$sendStacksOut();
        }
        accessor.insaneae$setRoundRobinIndex(accessor.insaneae$getRoundRobinIndex() + used);
        return done;
    }

    private static void removeFromHolder(KeyCounter[] inputHolder, AEKey key, long amount) {
        for (KeyCounter counter : inputHolder) {
            if (amount <= 0) {
                return;
            }
            long take = Math.min(counter.get(key), amount);
            if (take > 0) {
                counter.remove(key, take);
                amount -= take;
            }
        }
    }

    /**
     * まとめてよい状態なら搬入先の一覧 (AE2 と同じ面の順) を返す。だめなら null。
     */
    @Nullable
    private static List<Target> targetsIfEligible(InsanePatternProviderLogic logic, IPatternDetails details) {
        if (!InsaneAEConfig.processingBulkPush()) {
            return null;
        }
        BlockEntity be = logic.hostBlockEntity();
        Level level = be == null ? null : be.getLevel();
        if (level == null || level.isClientSide()) {
            return null;
        }
        if (!logic.isNodeActive() || !logic.hasPattern(details) || logic.isBusy()) {
            return null;
        }
        if (logic.isBlocking() || logic.getCraftingLockedReason() != LockCraftingMode.NONE) {
            return null;
        }
        LockCraftingMode lockMode = logic.getConfigManager().getSetting(Settings.LOCK_CRAFTING_MODE);
        if (lockMode == LockCraftingMode.LOCK_UNTIL_PULSE || lockMode == LockCraftingMode.LOCK_UNTIL_RESULT) {
            return null;
        }
        if (!details.supportsPushInputsToExternalInventory()) {
            return null;
        }

        var accessor = (PatternProviderLogicAccessor) logic;
        List<Target> targets = new ArrayList<>();
        for (Direction side : accessor.insaneae$getActiveSides()) {
            ICraftingMachine machine = ICraftingMachine.of(level, be.getBlockPos().relative(side), side.getOpposite());
            if (machine != null && machine.acceptsPlans()) {
                return null;
            }
            PatternProviderTarget adapter = accessor.insaneae$findAdapter(side);
            if (adapter != null) {
                targets.add(new Target(side, adapter));
            }
        }
        return targets.isEmpty() ? null : targets;
    }

    /** {@code wanted} 回を上限に、この搬入先が材料を全部受け取れる回数。 */
    private static long acceptedCrafts(PatternProviderTarget adapter, Map<AEKey, Long> perCraft, long wanted) {
        long crafts = probeCrafts(perCraft, wanted);
        for (Map.Entry<AEKey, Long> input : perCraft.entrySet()) {
            if (crafts <= 0) {
                return 0;
            }
            long perOne = input.getValue();
            long inserted = adapter.insert(input.getKey(), perOne * crafts, Actionable.SIMULATE);
            crafts = Math.min(crafts, inserted / perOne);
        }
        return Math.max(crafts, 0);
    }

    /** 1 回の照会で尋ねる回数。どの材料も {@link #PROBE_UNITS} を超えないようにする。 */
    private static long probeCrafts(Map<AEKey, Long> perCraft, long wanted) {
        long crafts = wanted;
        for (long perOne : perCraft.values()) {
            crafts = Math.min(crafts, PROBE_UNITS / perOne);
        }
        return crafts;
    }

    /**
     * パターンが宣言する 1 回ぶんの材料 (同じ材料はまとめる)。
     * 入力の候補が 1 種類でないものは null (まとめると取り出した材料と食い違う)。
     */
    @Nullable
    private static Map<AEKey, Long> perCraftFromPattern(IPatternDetails details) {
        Map<AEKey, Long> perCraft = new LinkedHashMap<>();
        try {
            for (IPatternDetails.IInput input : details.getInputs()) {
                GenericStack[] possible = input.getPossibleInputs();
                if (possible.length != 1 || possible[0].amount() <= 0 || input.getMultiplier() <= 0) {
                    return null;
                }
                long amount = Math.multiplyExact(possible[0].amount(), input.getMultiplier());
                perCraft.merge(possible[0].what(), amount, Math::addExact);
            }
        } catch (ArithmeticException overflow) {
            return null;
        }
        return perCraft.isEmpty() ? null : perCraft;
    }

    /**
     * 実際に取り出された材料を回数で割った 1 回ぶん。並びはパターンの入力順に揃える
     * (入れた順に枠が埋まる機械があるため)。割り切れなければ null。
     */
    @Nullable
    private static Map<AEKey, Long> perCraftFromHolder(IPatternDetails details, KeyCounter[] inputHolder,
            long times) {
        KeyCounter total = new KeyCounter();
        for (KeyCounter counter : inputHolder) {
            total.addAll(counter);
        }
        Map<AEKey, Long> perCraft = new LinkedHashMap<>();
        for (IPatternDetails.IInput input : details.getInputs()) {
            for (GenericStack possible : input.getPossibleInputs()) {
                long held = total.get(possible.what());
                if (held > 0 && !perCraft.containsKey(possible.what())) {
                    if (held % times != 0) {
                        return null;
                    }
                    perCraft.put(possible.what(), held / times);
                }
            }
        }
        // パターンに無い材料が混ざっていたら、配分を信用しない。
        for (var entry : total) {
            if (!perCraft.containsKey(entry.getKey())) {
                return null;
            }
        }
        return perCraft.isEmpty() ? null : perCraft;
    }
}
