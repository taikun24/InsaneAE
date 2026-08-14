package jp.main.taikun.insaneae.quantum;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.storage.MEStorage;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.InputTemplate;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;
import com.mojang.logging.LogUtils;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import jp.main.taikun.insaneae.integration.aco.AcoBigIntegerJobRegistry;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

/**
 * 同じパターンを 1 tick に何度も処理するときの高速経路。
 *
 * <p>AE2 の {@code CraftingCpuLogic.executeCrafting} は 1 クラフトごとに
 * 材料の取り出し・{@code KeyCounter} の生成・電力の消費・帳簿の更新を行うため、
 * クラフト数に比例したコストが必ずかかる。ここではそれを
 * <b>「N 回ぶんの材料をまとめて取り出し、1 回だけ組み立てて結果を N 倍する」</b>
 * に置き換える ({@link IBulkCraftingProvider} を実装したプロバイダ限定)。</p>
 *
 * <p>置き換えるのは AE2 と同じ処理内容 (材料の消費・電力・{@code waitingFor}・残り回数) で、
 * <b>回数のぶんだけ掛け算しているだけ</b>。1 回も処理できなければ 0 を返し、
 * AE2 本来の 1 回ずつの処理にそのまま任せる。</p>
 */
public final class QuantumBulkCrafting {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * まとめ処理が実際に走った回数 (窓の数)。<b>観測用のカウンタで、動作には使わない。</b>
     *
     * <p>まとめ処理は「速いだけで結果は同じ」なので、結果を見ても働いたか分からない。
     * 他 Mod が {@code executeCrafting} を先に打ち切ると<b>黙って素の 1 回ずつに戻る</b>ため、
     * 実際のジョブで発火しているかをゲームテストから見るための唯一の手掛かりになる
     * ({@code insaneae_bulk_execution_live})。</p>
     */
    public static volatile long bulkWindows;

    private QuantumBulkCrafting() {
    }

    /**
     * @param view どの Mod のクラフト CPU かを問わないジョブの窓口 ({@link CraftingJobView})。
     *             null なら何もしない。
     * @return まとめ処理で消費した<b>操作数</b>。通常はクラフト回数と同じだが、
     *         タスク統合 ({@link IBulkCraftingProvider#fusesOperations}) 中のプロバイダは
     *         何回まとめても 1 と数える。0 なら CPU 本来の処理を続行させること。
     */
    public static int execute(CraftingJobView view, int maxPatterns,
            CraftingService craftingService, IEnergyService energyService, Level level) {
        if (view == null || maxPatterns <= 0) {
            return 0;
        }

        Optional<AcoBigIntegerJobRegistry.CraftingCursor> exactTasks = view.exactTasks();
        if (exactTasks.isPresent()) {
            return executeExact(view, exactTasks.get(), maxPatterns,
                    craftingService, energyService, level);
        }

        int pushed = 0;
        CraftingJobView.TaskCursor cursor = view.tasks();
        // 予算を先に見るのが要点。next() はカーソルを進めてしまうので、
        // 使い切ったあとに呼ぶとそのタスクを 1 つ読み飛ばしたことになる。
        while (pushed < maxPatterns && cursor.next()) {
            long remaining = cursor.remaining();
            if (remaining <= 0) {
                cursor.remove();
                continue;
            }
            IPatternDetails details = cursor.details();

            IBulkCraftingProvider provider = findBulkProvider(craftingService, details);
            if (provider == null) {
                continue;
            }
            // タスク統合 (fusesOperations): まとめ 1 回を CPU 予算の 1 操作として数える。
            // このとき回数は CPU 予算 (maxPatterns) で縛らず、プロバイダ自身の予算に任せる。
            boolean fused = provider.fusesOperations();
            long limit = Math.min(remaining, provider.getBulkCapacity(details));
            if (!fused) {
                limit = Math.min(limit, maxPatterns - pushed);
            }
            limit = clampForOutputs(details, limit);
            if (limit <= 0) {
                continue;
            }

            long done = pushBulk(view, details, provider, limit, energyService, level);
            if (done > 0) {
                pushed += fused ? 1 : (int) done;
                long left = remaining - done;
                cursor.setRemaining(left);
                if (left <= 0) {
                    cursor.remove();
                }
                view.markDirty();
            }
        }

        return pushed;
    }

    /**
     * 完成品の計上 ({@code 出力数 × done}) が long からあふれない回数まで絞る。
     *
     * <p>統合会計では 1 回の {@code done} が Quantum CPU の予算 (最大 922京) まで育つので、
     * 出力が 2 個以上のレシピは素朴に掛けると必ずあふれる。飽和させると
     * {@code waitingFor} の帳簿と実際の完成品数がずれるため、<b>あふれない回数しか組まない</b>。</p>
     */
    private static long clampForOutputs(IPatternDetails details, long limit) {
        for (GenericStack output : details.getOutputs()) {
            long amount = output.amount();
            if (amount > 1) {
                limit = Math.min(limit, Long.MAX_VALUE / amount);
            }
        }
        return limit;
    }

    /**
     * ACOの正確なTask回数を、AE2の一tick予算以下のlong窓へ変換する。
     * 物理的な材料は各窓だけMEから取り出すため、CPU在庫へ全BigInteger量を載せない。
     */
    private static int executeExact(CraftingJobView view,
            AcoBigIntegerJobRegistry.CraftingCursor cursor,
            int maxPatterns,
            CraftingService craftingService,
            IEnergyService energyService,
            Level level) {
        int pushed = 0;
        while (pushed < maxPatterns && cursor.next()) {
            BigInteger remaining = cursor.remaining();
            if (remaining.signum() <= 0) {
                cursor.remove();
                continue;
            }
            IPatternDetails details = cursor.details();
            IBulkCraftingProvider provider = findBulkProvider(craftingService, details);
            if (provider == null) {
                // 親Patternの材料がまだMEへ戻っていなくても、同じ窓の別Patternは進められる。
                continue;
            }
            long boundedRemaining = remaining.min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
            long limit = Math.min(
                    Math.min(boundedRemaining, provider.getBulkCapacity(details)),
                    maxPatterns - pushed);
            if (limit <= 0L) {
                // このプロバイダのtick予算が尽きた場合は、次の窓で同じTaskを再試行する。
                continue;
            }
            long done = pushBulk(view, details, provider, limit, energyService, level);
            if (done <= 0L) {
                // 材料搬入待ちのTaskで全Exact Jobを止めず、依存元Patternを先に進める。
                continue;
            }
            cursor.setRemaining(remaining.subtract(BigInteger.valueOf(done)));
            pushed += Math.toIntExact(done);
            if (cursor.remaining().signum() <= 0) {
                cursor.remove();
            }
            view.markDirty();
        }
        return pushed;
    }

    /** 材料の取り出しから帳簿の更新まで。処理できた回数を返す。 */
    private static long pushBulk(CraftingJobView view, IPatternDetails details,
            IBulkCraftingProvider provider, long limit, IEnergyService energyService, Level level) {
        ListCraftingInventory inventory = view.getInventory();
        KeyCounter containerItems = new KeyCounter();
        boolean exactWindow = view.hasExactCraftingPlan();
        // AE2のwaitingForはlong固定なので、1窓の出力だけはlongへ正確に戻せる範囲に制限する。
        limit = Math.min(limit, getSafeOutputWindow(details));
        if (limit <= 0L) {
            return 0;
        }

        // 電力が足りなければ回数を減らして 1 度だけやり直す。
        for (int attempt = 0; attempt < 2 && limit > 0; attempt++) {
            containerItems.reset();
            Extracted extracted = extractInputs(
                    view, details, inventory, level, limit, exactWindow, containerItems);
            if (extracted == null) {
                return 0;
            }
            // <b>取り出せた回数を使うこと。</b>在庫が足りなければ extractInputs が回数を減らすので、
            // ここで limit をそのまま使うと「10 回ぶんの材料で 1000 回ぶんの完成品」になる。
            KeyCounter[] inputs = extracted.inputs();
            long times = extracted.times();

            double power = CraftingCpuHelper.calculatePatternPower(inputs);
            if (power > 0.0) {
                double available = energyService.extractAEPower(power, Actionable.SIMULATE, PowerMultiplier.CONFIG);
                if (available < power - 0.01) {
                    // 取りすぎたので全部戻してから、賄える回数で取り直す。
                    rollbackInputs(view, inputs, extracted.networkInputs());
                    limit = (long) (times * (available / power));
                    continue;
                }
                energyService.extractAEPower(power, Actionable.MODULATE, PowerMultiplier.CONFIG);
            }

            long done = provider.pushPatternBulk(details, inputs, times);
            if (done <= 0) {
                // 受け取ってもらえなかった: 電力は戻せないが材料は戻す。
                rollbackInputs(view, inputs, extracted.networkInputs());
                return 0;
            }

            bulkWindows++;
            ListCraftingInventory waitingFor = view.getWaitingFor();
            for (GenericStack output : details.getOutputs()) {
                // getSafeOutputWindowで検査済みだが、境界をコード上でも再確認してwrapを防ぐ。
                waitingFor.insert(output.what(), Math.multiplyExact(output.amount(), done),
                        Actionable.MODULATE);
            }
            Object tracker = view.getTimeTracker();
            for (var entry : containerItems) {
                waitingFor.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
                TimeTrackerAdapter.addMaxItems(tracker, entry.getLongValue(), entry.getKey().getType());
            }
            return done;
        }

        return 0;
    }

    /**
     * {@code times} 回ぶんの材料をまとめて取り出す。足りなければ取れるぶんまで減らす。
     *
     * <p>AE2 の {@code CraftingCpuHelper.extractPatternInputs} とほぼ同じだが、
     * <b>1 つの材料につき代替候補を 1 種類しか使わない</b>点が違う。
     * 混ざると「1 回組み立てて結果を N 倍する」が成り立たなくなるため
     * (代替材料によって完成品が変わるレシピがある)。</p>
     *
     * <p><b>取り出せた回数を必ず一緒に返すこと</b> ({@link Extracted})。ここは在庫が足りなければ
     * 回数を黙って減らすので、呼び出し側が要求した回数のまま組ませるとアイテムが増える。</p>
     *
     * @return 取り出した材料と、実際に取り出せた回数。1 回ぶんも取れなければ null。
     */
    private static Extracted extractInputs(CraftingJobView view, IPatternDetails details,
            ListCraftingInventory inventory,
            Level level, long times, boolean exactWindow, KeyCounter containerItems) {
        IPatternDetails.IInput[] inputs = details.getInputs();
        MEStorage networkStorage = exactWindow ? view.getNetworkStorage() : null;
        var actionSource = exactWindow ? view.getActionSource() : null;
        KeyCounter networkInputs = new KeyCounter();

        // 1) 何回ぶん取れるか調べる (在庫を触らない)
        for (IPatternDetails.IInput input : inputs) {
            long perCraft = input.getMultiplier();
            if (perCraft <= 0L) {
                return null;
            }
            long units = 0;
            for (InputTemplate template : getValidTemplates(inventory, input, level, exactWindow)) {
                if (template.amount() <= 0L) {
                    continue;
                }
                long available = inventory.extract(template.key(), Long.MAX_VALUE, Actionable.SIMULATE);
                // Exact JobはCPU在庫が空でも、現在の窓だけMEから直接補充できる。
                if (networkStorage != null && actionSource != null) {
                    long networkAvailable = networkStorage.extract(
                            template.key(), Long.MAX_VALUE, Actionable.SIMULATE, actionSource);
                    available = saturatedAdd(available, networkAvailable);
                }
                long candidate = available / template.amount();
                if (candidate > 0) {
                    units = candidate;
                    break;
                }
            }
            times = Math.min(times, units / perCraft);
            // template.amount * perCraft * times をlongへ戻す境界も先に制限する。
            if (times > 0L) {
                for (InputTemplate template : getValidTemplates(inventory, input, level, exactWindow)) {
                    if (template.amount() > 0L) {
                        times = Math.min(times, Long.MAX_VALUE / template.amount() / perCraft);
                        break;
                    }
                }
            }
            if (times <= 0) {
                return null;
            }
        }

        // 2) 実際に取り出す
        // <b>代替候補は入力1つにつき1種類だけ。</b>まとめ処理は「1回分の組立結果 × N回」で
        // 出力を計上するため、1つの窓に複数の代替素材を混ぜると組立結果と食い違いうる。
        // 候補は 1) と同じ規則 (CPU在庫 + Exact窓ならME在庫の合算) で選び直す。
        KeyCounter[] holder = new KeyCounter[inputs.length];
        try {
            for (int x = 0; x < inputs.length; x++) {
                KeyCounter list = holder[x] = new KeyCounter();
                long needed = Math.multiplyExact(inputs[x].getMultiplier(), times);
                InputTemplate chosen = null;
                for (InputTemplate template : getValidTemplates(inventory, inputs[x], level, exactWindow)) {
                    if (template.amount() <= 0L) {
                        continue;
                    }
                    long available = inventory.extract(template.key(), Long.MAX_VALUE, Actionable.SIMULATE);
                    if (networkStorage != null && actionSource != null) {
                        available = saturatedAdd(available, networkStorage.extract(
                                template.key(), Long.MAX_VALUE, Actionable.SIMULATE, actionSource));
                    }
                    if (available / template.amount() > 0) {
                        chosen = template;
                        break;
                    }
                }
                if (chosen == null) {
                    // 1) の見積もりと食い違った: 何も無かったことにして通常経路に任せる。
                    LOGGER.warn("InsaneAE: bulk crafting could not gather ingredients for {}, falling back.",
                            details.getDefinition());
                    rollbackInputs(view, holder, networkInputs);
                    return null;
                }
                long extracted = CraftingCpuHelper.extractTemplates(inventory, chosen, needed);
                if (extracted > 0) {
                    addExact(list, chosen.key(), Math.multiplyExact(extracted, chosen.amount()));
                    AEKey containerItem = inputs[x].getRemainingKey(chosen.key());
                    if (containerItem != null) {
                        addExact(containerItems, containerItem, extracted);
                    }
                    needed -= extracted;
                }
                // Exact JobはCPU在庫で足りないぶんを、この窓だけMEから直接補充できる。
                if (needed > 0L && networkStorage != null && actionSource != null) {
                    long networkAmount = Math.multiplyExact(needed, chosen.amount());
                    long networkExtracted = networkStorage.extract(
                            chosen.key(), networkAmount, Actionable.MODULATE, actionSource);
                    if (networkExtracted > 0L) {
                        addExact(networkInputs, chosen.key(), networkExtracted);
                        if (networkExtracted % chosen.amount() != 0L) {
                            // 部分単位の入力は同じパターンへ安全に載せられないため、消費を戻して失敗扱いにする。
                            rollbackInputs(view, holder, networkInputs);
                            return null;
                        }
                        long networkUnits = networkExtracted / chosen.amount();
                        addExact(list, chosen.key(), networkExtracted);
                        AEKey containerItem = inputs[x].getRemainingKey(chosen.key());
                        if (containerItem != null) {
                            addExact(containerItems, containerItem, networkUnits);
                        }
                        needed -= networkUnits;
                    }
                }
                if (needed > 0) {
                    // 1) の見積もりと食い違った: 何も無かったことにして通常経路に任せる。
                    LOGGER.warn("InsaneAE: bulk crafting could not gather ingredients for {}, falling back.",
                            details.getDefinition());
                    rollbackInputs(view, holder, networkInputs);
                    return null;
                }
            }
        } catch (ArithmeticException overflow) {
            // 実際に選ばれた候補の掛け算がlongへ戻らない窓は、材料を戻して次tickへ回す。
            LOGGER.warn("InsaneAE: bulk crafting window exceeded the AE2 long boundary for {}, falling back.",
                    details.getDefinition());
            rollbackInputs(view, holder, networkInputs);
            return null;
        }
        return new Extracted(holder, networkInputs, times);
    }

    /**
     * Exact窓ではCPU在庫が空でもPatternに記録された候補を使えるようにする。
     * 通常窓はAE2の候補列挙をそのまま使い、タグ・Fuzzyの意味を変更しない。
     */
    private static Iterable<InputTemplate> getValidTemplates(
            ListCraftingInventory inventory,
            IPatternDetails.IInput input,
            Level level,
            boolean exactWindow) {
        if (!exactWindow) {
            return CraftingCpuHelper.getValidItemTemplates(inventory, input, level);
        }
        List<InputTemplate> templates = new ArrayList<>();
        for (InputTemplate template : CraftingCpuHelper.getValidItemTemplates(inventory, input, level)) {
            templates.add(template);
        }
        // Exact窓ではネットワーク側の候補をPatternから直接復元する。
        for (GenericStack possible : input.getPossibleInputs()) {
            if (possible != null && possible.amount() > 0L && input.isValid(possible.what(), level)) {
                boolean duplicate = false;
                for (InputTemplate current : templates) {
                    if (current.key().equals(possible.what())
                            && current.amount() == possible.amount()) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    templates.add(new InputTemplate(possible.what(), possible.amount()));
                }
            }
        }
        return templates;
    }

    /** CPU在庫とExact窓でMEから取り出した材料を、失敗時に同じ所有者へ戻す。 */
    private static void rollbackInputs(
            CraftingJobView view,
            KeyCounter[] cpuInputs,
            KeyCounter networkInputs) {
        // holderにはCPU在庫分とMEから借りた分が混在するため、ME分を先に分離する。
        // そのまま両方へ戻すと、同じ材料を二重に復元してしまう。
        for (var networkEntry : networkInputs) {
            long remaining = networkEntry.getLongValue();
            for (KeyCounter counter : cpuInputs) {
                if (remaining <= 0L) {
                    break;
                }
                long held = counter.get(networkEntry.getKey());
                long remove = Math.min(held, remaining);
                if (remove > 0L) {
                    counter.remove(networkEntry.getKey(), remove);
                    remaining -= remove;
                }
            }
        }
        CraftingCpuHelper.reinjectPatternInputs(view.getInventory(), cpuInputs);
        MEStorage storage = view.getNetworkStorage();
        var source = view.getActionSource();
        if (storage == null || source == null) {
            return;
        }
        for (var entry : networkInputs) {
            long inserted = storage.insert(
                    entry.getKey(), entry.getLongValue(), Actionable.MODULATE, source);
            if (inserted != entry.getLongValue()) {
                LOGGER.error("InsaneAE: could not fully roll back {} of {} after bulk crafting failure.",
                        entry.getLongValue() - inserted, entry.getKey());
            }
        }
    }

    /** 1窓の出力がAE2のlong会計を超えないように上限を求める。 */
    private static long getSafeOutputWindow(IPatternDetails details) {
        long limit = Long.MAX_VALUE;
        for (GenericStack output : details.getOutputs()) {
            if (output.amount() <= 0L) {
                return 0L;
            }
            limit = Math.min(limit, Long.MAX_VALUE / output.amount());
        }
        return limit;
    }

    /** 加算結果を負数へ折り返さず、窓の上限Long.MAX_VALUEへ飽和させる。 */
    private static long saturatedAdd(long left, long right) {
        if (right <= 0L) {
            return left;
        }
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    /** KeyCounterへ追加する前に個別キーのlong境界を検査する。 */
    private static void addExact(KeyCounter counter, AEKey key, long amount) {
        if (amount < 0L || counter.get(key) > Long.MAX_VALUE - amount) {
            throw new ArithmeticException("bulk crafting counter overflow for " + key);
        }
        counter.add(key, amount);
    }

    /**
     * {@link #extractInputs} の戻り値。
     *
     * @param inputs 取り出した材料
     * @param times  <b>実際に</b>取り出せた回数。要求した回数とは限らない
     */
    private record Extracted(KeyCounter[] inputs, KeyCounter networkInputs, long times) {
    }

    private static IBulkCraftingProvider findBulkProvider(CraftingService craftingService, IPatternDetails details) {
        for (ICraftingProvider provider : craftingService.getProviders(details)) {
            if (provider instanceof IBulkCraftingProvider bulk && !provider.isBusy()
                    && bulk.getBulkCapacity(details) > 0) {
                return bulk;
            }
        }
        return null;
    }
}
