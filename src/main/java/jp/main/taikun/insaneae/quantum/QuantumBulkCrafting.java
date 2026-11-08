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
import jp.main.taikun.insaneae.config.InsaneAEConfig;
import jp.main.taikun.insaneae.integration.aco.AcoBigIntegerJobRegistry;
import java.util.ArrayList;
import java.util.List;
import java.math.BigInteger;
import java.util.Optional;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import jp.main.taikun.insaneae.util.LogOnce;
import jp.main.taikun.insaneae.util.SaturatingMath;

/**
 * 同じパターンを1 tickに何度も処理するときの高速経路。
 *
 * <p>AE2の {@code CraftingCpuLogic.executeCrafting} は1クラフトごとに
 * 材料の取り出し・{@code KeyCounter} の生成・電力の消費・帳簿の更新を行うため、
 * クラフト数に比例したコストが必ずかかる。ここではそれを
 * 「N回ぶんの材料をまとめて取り出し、1回だけ組み立てて結果をN倍する」
 * に置き換える。{@link IBulkCraftingProvider}を実装したプロバイダと、
 * {@link BulkCraftingProviderAdapters} にアダプタが登録されたプロバイダ限定)。</p>
 *
 * <p>置き換えるのはAE2と同じ処理内容、つまり材料の消費、電力、{@code waitingFor}、残り回数で、
 * 回数のぶんだけ掛け算しているだけ。1回も処理できなければ0を返し、
 * AE2本来の1回ずつの処理にそのまま任せる。</p>
 */
public final class QuantumBulkCrafting {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * まとめ処理が実際に走った回数、つまり窓の数。観測用のカウンタで、動作には使わない。
     *
     * <p>まとめ処理は「速いだけで結果は同じ」なので、結果を見ても働いたか分からない。
     * 他Modが {@code executeCrafting} を先に打ち切ると黙って素の1回ずつに戻るため、
     * 実際のジョブで発火しているかをゲームテストから見るための唯一の手掛かりになる
     * ({@code insaneae_bulk_execution_live})。</p>
     */
    public static volatile long bulkWindows;

    /** 窓1枚のlong上限。BigIntegerの残りをこの単位へ切り出す。 */
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private QuantumBulkCrafting() {
    }

    /**
     * @param viewどのModのクラフトCPUかを問わないジョブの窓口 ({@link CraftingJobView})。
     *             nullなら何もしない。
     * @returnまとめ処理で消費した操作数。通常はクラフト回数と同じだが、
     *         タスク統合 ({@link BulkCraftingHandler#fusesOperations}) 中のプロバイダは
     *         何回まとめても1と数える。0ならCPU本来の処理を続行させること。
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
        // 使い切ったあとに呼ぶとそのタスクを1つ読み飛ばしたことになる
        while (pushed < maxPatterns && cursor.next()) {
            long remaining = cursor.remaining();
            if (remaining <= 0) {
                cursor.remove();
                continue;
            }
            IPatternDetails details = cursor.details();

            // 同じパターンを持つプロバイダが複数あれば順に回す。加工パターンは搬入先が
            // 埋まった時点でそのプロバイダの受け入れ回数が0になり、次のプロバイダが選ばれる
            while (remaining > 0 && pushed < maxPatterns) {
                BulkCraftingHandler provider = findBulkProvider(craftingService, details);
                if (provider == null) {
                    break;
                }
                // タスク統合 (fusesOperations): まとめ1回をCPU予算の1操作として数える
                // このとき回数はCPU予算 (maxPatterns) で縛らず、プロバイダ自身の予算に任せる
                boolean fused = provider.fusesOperations();
                long limit = Math.min(remaining, provider.getBulkCapacity(details));
                if (!fused) {
                    limit = Math.min(limit, maxPatterns - pushed);
                }
                limit = clampForOutputs(details, limit);
                if (limit <= 0) {
                    break;
                }

                long done = pushBulk(view, details, provider, limit, energyService, level);
                if (done <= 0) {
                    break;
                }
                pushed += fused ? 1 : (int) done;
                remaining -= done;
                cursor.setRemaining(remaining);
                view.markDirty();
            }
            if (remaining <= 0) {
                cursor.remove();
            }
        }

        return pushed;
    }

    /**
     * 完成品の計上、{@code出力数 × done}がlongからあふれない回数まで絞る。
     *
     * <p>統合会計では1回の{@code done}がQuantum CPUの予算、最大922京まで育つので、
     * 出力が2個以上のレシピは素朴に掛けると必ずあふれる。飽和させると
     * {@code waitingFor} の帳簿と実際の完成品数がずれるため、あふれない回数しか組まない。</p>
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
        // 1 tickに使える窓の総数。パターンをまたいで共有する
        // 1本のパターンが全部使い切ると、次の段が材料をもらえないままtickが終わる
        WindowBudget windows = new WindowBudget(InsaneAEConfig.maxCraftingWindowsPerTick());
        int pushed = 0;
        boolean drainWholeTree = false;
        boolean progressed = true;

        // 加速カード満載 + タスク統合のときは、木の下から上へ何度も往復する
        // 1周では「下の段を作ってから上の段が使う」という1段ぶんしか進まないので、
        // 往復しないと段数のぶんだけtickがかかる
        // 往復の回数ではなく窓の総数で頭打ちにするので、1 tickの重さは変わらない
        // この呼び出しで触ったプロバイダ。周回の合間に完成品を清算するために覚えておく
        Set<BulkCraftingHandler> touched =
                Collections.newSetFromMap(new IdentityHashMap<>());

        while (progressed && windows.remaining() > 0) {
            progressed = false;
            /*
             * 周回の頭で完成品を流しておく。組み上がったものは一旦プロバイダの
             * 保留分に溜まり、MEへ入るまで次の段からは見えない。清算しないと
             * 「下の段は作ったのに上の段が材料切れ」で1周ごとに空振りし、
             * 1 tickに1段しか進まない。
             */
            for (BulkCraftingHandler settled : touched) {
                settled.settleCompletedOutputs();
            }
            cursor.rewind();
            while ((drainWholeTree || pushed < maxPatterns) && cursor.next()) {
                BigInteger remaining = cursor.remaining();
                if (remaining.signum() <= 0) {
                    cursor.remove();
                    continue;
                }
                IPatternDetails details = cursor.details();
                BulkCraftingHandler provider = findBulkProvider(craftingService, details);
                if (provider == null) {
                    // 親Patternの材料がまだMEへ戻っていなくても、同じ窓の別Patternは進められる
                    continue;
                }
                // タスク統合 (fusesOperations): 通常経路と同じく、まとめ1回をCPU予算の
                // 1操作として数え、回数はプロバイダ自身の予算に任せる
                // BigInteger経路こそ1窓が大きいので、ここを飛ばすと統合カードが反映されない
                // 直前の窓で組んだぶんが保留のままだと、プロバイダは「取り込み中」を名乗って
                // 予算0を返す。周回のたびに清算しておかないと1 tickに1窓しか進まない
                if (touched.contains(provider)) {
                    provider.settleCompletedOutputs();
                }
                boolean fused = provider.fusesOperations();
                // 「アップグレード満載」= 統合カード + 加速カード7枚。この組み合わせのときだけ、
                // CPUの1 tick予算 (long/tick) ではなく窓の総数だけで区切る
                if (fused && provider.repeatsWindowsWithinTick()) {
                    drainWholeTree = true;
                }
                long boundedRemaining = remaining.min(LONG_MAX).longValueExact();
                long limit = Math.min(boundedRemaining, provider.getBulkCapacity(details));
                if (!fused) {
                    limit = Math.min(limit, maxPatterns - pushed);
                }
                limit = clampForOutputs(details, limit);
                if (limit <= 0L) {
                    // このプロバイダのtick予算が尽きた場合は、次の窓で同じTaskを再試行する
                    continue;
                }
                long done = pushBulk(view, details, provider, limit, energyService, level);
                if (done <= 0L) {
                    // 材料搬入待ちのTaskで全Exact Jobを止めず、依存元Patternを先に進める
                    continue;
                }
                touched.add(provider);
                windows.spend();
                progressed = true;
                BigInteger left = remaining.subtract(BigInteger.valueOf(done));
                left = repeatWindows(view, details, provider, fused, left, windows,
                        energyService, level);
                cursor.setRemaining(left);
                // 統合中はdoneがintを超えうるので、toIntExactに渡さないこと
                pushed += fused ? 1 : Math.toIntExact(done);
                if (cursor.remaining().signum() <= 0) {
                    cursor.remove();
                }
                view.markDirty();
            }
            // 往復するのは木を下から上へ流すためなので、統合していない
            // 1クラフト1操作でCPU予算に従うときは1周で終える
            if (!drainWholeTree) {
                break;
            }
        }
        return pushed;
    }

    /**
     * 1 tickに使える窓の総数。パターンをまたいで共有する予算。
     *
     * <p>これが無いと、一番下の段が窓を全部使い切って上の段に回らない。
     * 「1 tickの重さ」を決めるのはここだけにしておき、往復の回数は縛らない。</p>
     */
    private static final class WindowBudget {
        private int remaining;

        WindowBudget(int windows) {
            this.remaining = Math.max(windows, 1);
        }

        int remaining() {
            return remaining;
        }

        void spend() {
            if (remaining > 0) {
                remaining--;
            }
        }
    }

    /**
     * 加速カード満載のとき、同じパターンの窓を1 tickのうちに重ねて残りを削る。
     *
     * <p>1窓に組める回数はlongの会計で頭打ちになる。完成品は
     * {@code Long.MAX_VALUE / 出力数}、材料の取り出しも同様) ので、BigInteger級の残りは
     * 1窓では終わらない。窓と窓の間で {@link BulkCraftingHandler#settleCompletedOutputs()} を
     * 呼び、組み上がった完成品をネットワークへ流してクラフトCPUの完成待ちを清算する。
     * こうするとどの瞬間の帳簿もlongに収まったまま、1 tickの合計だけがlongを超えられる。</p>
     *
     * <p>タスク統合カードが要る。統合していないと1クラフト = 1操作なので、
     * 窓を重ねた時点でクラスタの1 tick予算 (int) を超えてしまう。</p>
     *
     * <p>窓の数は {@code maxCraftingWindowsPerTick} で頭打ちにする。ここが無いと
     * 終わるまで回し続けて、サーバが1 tickから戻ってこなくなるおそれがある。
     * 途中で完成品が入らなくなったとき、電力、在庫、ジョブ完了のいずれかでも打ち切る。</p>
     *
     * @return削り切れずに残った回数
     */
    private static BigInteger repeatWindows(CraftingJobView view, IPatternDetails details,
            BulkCraftingHandler provider, boolean fused, BigInteger remaining,
            WindowBudget windows, IEnergyService energyService, Level level) {
        if (!fused || !provider.repeatsWindowsWithinTick()) {
            return remaining;
        }
        while (windows.remaining() > 0 && remaining.signum() > 0) {
            // 先に清算する。これをしないと完成待ちが積み上がってlongを溢れさせる
            provider.settleCompletedOutputs();

            long bounded = remaining.min(LONG_MAX).longValueExact();
            long limit = Math.min(bounded, provider.getBulkCapacity(details));
            limit = clampForOutputs(details, limit);
            if (limit <= 0L) {
                break;
            }
            long done = pushBulk(view, details, provider, limit, energyService, level);
            if (done <= 0L) {
                // 材料切れ・電力切れ・ジョブ完了。同じtickの別パターンへ回す
                break;
            }
            windows.spend();
            remaining = remaining.subtract(BigInteger.valueOf(done));
        }
        return remaining;
    }

    /** 材料の取り出しから帳簿の更新まで。処理できた回数を返す。 */
    private static long pushBulk(CraftingJobView view, IPatternDetails details,
            BulkCraftingHandler provider, long limit, IEnergyService energyService, Level level) {
        ListCraftingInventory inventory = view.getInventory();
        KeyCounter containerItems = new KeyCounter();
        boolean exactWindow = view.hasExactCraftingPlan();
        // AE2のwaitingForはlong固定なので、1窓の出力だけはlongへ正確に戻せる範囲に制限する
        limit = Math.min(limit, getSafeOutputWindow(view, details));
        if (limit <= 0L) {
            return 0;
        }

        // 電力が足りなければ回数を減らして1度だけやり直す
        for (int attempt = 0; attempt < 2 && limit > 0; attempt++) {
            containerItems.reset();
            Extracted extracted = extractInputs(
                    view, details, inventory, level, limit, exactWindow, containerItems);
            if (extracted == null) {
                return 0;
            }
            // 取り出せた回数を使うこと。在庫が足りなければextractInputsが回数を減らすので、
            // ここでlimitをそのまま使うと「10回ぶんの材料で1000回ぶんの完成品」になる
            KeyCounter[] inputs = extracted.inputs();
            long times = extracted.times();

            double power = CraftingCpuHelper.calculatePatternPower(inputs);
            if (power > 0.0) {
                double available = energyService.extractAEPower(power, Actionable.SIMULATE, PowerMultiplier.CONFIG);
                if (available < power - 0.01) {
                    // 取りすぎたので全部戻してから、賄える回数で取り直す
                    rollbackInputs(view, inputs, extracted.networkInputs());
                    limit = (long) (times * (available / power));
                    continue;
                }
                energyService.extractAEPower(power, Actionable.MODULATE, PowerMultiplier.CONFIG);
            }

            long done = Math.min(provider.pushPatternBulk(details, inputs, times), times);
            if (done <= 0) {
                // 受け取ってもらえなかった: 電力は戻せないが材料は戻す
                rollbackInputs(view, inputs, extracted.networkInputs());
                return 0;
            }
            if (done < times) {
                // 搬入先が途中で埋まった。加工パターンの場合。使われなかった材料はinputsに残っている
                rollbackInputs(view, inputs, extracted.networkInputs());
                if (power > 0.0) {
                    energyService.injectPower(power * (times - done) / times, Actionable.MODULATE);
                }
                scaleContainerItems(containerItems, times, done);
            }

            bulkWindows++;
            ListCraftingInventory waitingFor = view.getWaitingFor();
            for (GenericStack output : details.getOutputs()) {
                // getSafeOutputWindowで検査済みだが、境界をコード上でも再確認してwrapを防ぐ
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
     * <p>AE2の {@code CraftingCpuHelper.extractPatternInputs} とほぼ同じだが、
     * 1つの材料につき代替候補を1種類しか使わない点が違う。
     * 混ざると「1回組み立てて結果をN倍する」が成り立たなくなるため
     * 代替材料によって完成品が変わるレシピがある。</p>
     *
     * <p>取り出せた回数を必ず一緒に返すこと ({@link Extracted})。ここは在庫が足りなければ
     * 回数を黙って減らすので、呼び出し側が要求した回数のまま組ませるとアイテムが増える。</p>
     *
     * @return取り出した材料と、実際に取り出せた回数。1回ぶんも取れなければnull。
     */
    private static Extracted extractInputs(CraftingJobView view, IPatternDetails details,
            ListCraftingInventory inventory,
            Level level, long times, boolean exactWindow, KeyCounter containerItems) {
        IPatternDetails.IInput[] inputs = details.getInputs();
        MEStorage networkStorage = exactWindow ? view.getNetworkStorage() : null;
        var actionSource = exactWindow ? view.getActionSource() : null;
        KeyCounter networkInputs = new KeyCounter();

        // 1) 何回ぶん取れるか調べる。在庫を触らない
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
                // Exact JobはCPU在庫が空でも、現在の窓だけMEから直接補充できる
                if (networkStorage != null && actionSource != null) {
                    long networkAvailable = networkStorage.extract(
                            template.key(), Long.MAX_VALUE, Actionable.SIMULATE, actionSource);
                    available = SaturatingMath.add(available, networkAvailable);
                }
                long candidate = available / template.amount();
                if (candidate > 0) {
                    units = candidate;
                    break;
                }
            }
            times = Math.min(times, units / perCraft);
            // template.amount * perCraft * timesをlongへ戻す境界も先に制限する
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
        // 代替候補は入力1つにつき1種類だけ。まとめ処理は「1回分の組立結果 × N回」で
        // 出力を計上するため、1つの窓に複数の代替素材を混ぜると組立結果と食い違いうる
        // 候補は1)と同じ規則で選び直す。CPU在庫、Exact窓ならME在庫の合算
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
                        available = SaturatingMath.add(available, networkStorage.extract(
                                template.key(), Long.MAX_VALUE, Actionable.SIMULATE, actionSource));
                    }
                    if (available / template.amount() > 0) {
                        chosen = template;
                        break;
                    }
                }
                if (chosen == null) {
                    // 1) の見積もりと食い違った: 何も無かったことにして通常経路に任せる
                    logFallback("could not gather ingredients", details);
                    rollbackInputs(view, holder, networkInputs);
                    return null;
                }
                // 取り出した後に記録で溢れると戻し先を失うので、掛け算は取り出す前に済ませる
                Math.multiplyExact(needed, chosen.amount());
                long extracted = CraftingCpuHelper.extractTemplates(inventory, chosen, needed);
                if (extracted > 0) {
                    addExact(list, chosen.key(), Math.multiplyExact(extracted, chosen.amount()));
                    AEKey containerItem = inputs[x].getRemainingKey(chosen.key());
                    if (containerItem != null) {
                        addExact(containerItems, containerItem, extracted);
                    }
                    needed -= extracted;
                }
                // Exact JobはCPU在庫で足りないぶんを、この窓だけMEから直接補充できる
                if (needed > 0L && networkStorage != null && actionSource != null) {
                    long networkAmount = Math.multiplyExact(needed, chosen.amount());
                    // MEから取り出した後に記録で溢れると、記録できなかったぶんが消える。先に確かめる
                    ensureCanAdd(networkInputs, chosen.key(), networkAmount);
                    ensureCanAdd(list, chosen.key(), networkAmount);
                    long networkExtracted = networkStorage.extract(
                            chosen.key(), networkAmount, Actionable.MODULATE, actionSource);
                    if (networkExtracted > 0L) {
                        // 巻き戻しはholderを返却量の上限にするので、端数で諦める場合も先にholderへ入れる
                        addExact(networkInputs, chosen.key(), networkExtracted);
                        addExact(list, chosen.key(), networkExtracted);
                        if (networkExtracted % chosen.amount() != 0L) {
                            // 部分単位の入力は同じパターンへ安全に載せられないため、消費を戻して失敗扱いにする
                            rollbackInputs(view, holder, networkInputs);
                            return null;
                        }
                        long networkUnits = networkExtracted / chosen.amount();
                        AEKey containerItem = inputs[x].getRemainingKey(chosen.key());
                        if (containerItem != null) {
                            addExact(containerItems, containerItem, networkUnits);
                        }
                        needed -= networkUnits;
                    }
                }
                if (needed > 0) {
                    // 1) の見積もりと食い違った: 何も無かったことにして通常経路に任せる
                    logFallback("could not gather ingredients", details);
                    rollbackInputs(view, holder, networkInputs);
                    return null;
                }
            }
        } catch (ArithmeticException overflow) {
            // 実際に選ばれた候補の掛け算がlongへ戻らない窓は、材料を戻して次tickへ回す
            logFallback("window exceeded the AE2 long boundary", details);
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
        // Exact窓ではネットワーク側の候補をPatternから直接復元する
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

    /**
     * 使われなかった材料を取り出し元へ戻す。
     *
     * <p>{@code unused} はCPU在庫分とMEから借りた分が混ざった未使用の残りで、
     * 部分処理の後は借りた量より少ないことがある。借りた量をそのままMEへ戻すと
     * 消費済みのぶんまで戻って増殖するので、MEへは未使用量を上限に先に戻し、残りをCPU在庫へ戻す。
     * {@code unused} には途中で失敗したときのnullが混ざり得る。</p>
     */
    private static void rollbackInputs(
            CraftingJobView view,
            KeyCounter[] unused,
            KeyCounter networkInputs) {
        ListCraftingInventory inventory = view.getInventory();
        MEStorage storage = view.getNetworkStorage();
        var source = view.getActionSource();
        if (storage != null && source != null) {
            for (var networkEntry : networkInputs) {
                AEKey key = networkEntry.getKey();
                long toReturn = takeFromHolder(unused, key, networkEntry.getLongValue());
                if (toReturn <= 0L) {
                    continue;
                }
                long inserted = storage.insert(key, toReturn, Actionable.MODULATE, source);
                if (inserted < toReturn) {
                    // MEが満杯でも消さずにCPU在庫へ残す
                    LOGGER.warn("InsaneAE: could not return {} of {} to the network after bulk crafting; kept in the CPU.",
                            toReturn - inserted, key);
                    inventory.insert(key, toReturn - inserted, Actionable.MODULATE);
                }
            }
        }
        for (KeyCounter counter : unused) {
            if (counter == null) {
                continue;
            }
            for (var entry : counter) {
                if (entry.getLongValue() > 0L) {
                    inventory.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
                }
            }
            counter.reset();
        }
    }

    /** holderから最大 {@code amount} 取り除き、取り除けた量を返す。 */
    private static long takeFromHolder(KeyCounter[] holder, AEKey key, long amount) {
        long taken = 0L;
        for (KeyCounter counter : holder) {
            if (counter == null || taken >= amount) {
                continue;
            }
            long take = Math.min(counter.get(key), amount - taken);
            if (take > 0L) {
                counter.remove(key, take);
                taken += take;
            }
        }
        return taken;
    }

    /**
     * 1窓の出力がAE2のlong会計を超えないように上限を求める。
     *
     * <p>1窓ぶんの掛け算だけを見てはいけない。組み上がった出力はCPUの
     * クラフト在庫 ({@code ListCraftingInventory} = {@code KeyCounter}) に積まれ、
     * そこはキーごとにlongである。窓を重ねると1窓が安全でも
     * 積算がlongを折り返し、作った中間素材がまるごと消える。
     * 消えたぶんは誰も作り直さないので、次の段が材料待ちのまま永久に止まる。</p>
     *
     * <p>実際、8倍の段を重ねた木、つまり中間素材がlongを超えるものがこれで停止した。
     * 中間素材を使う側が減らすまで待てば良いだけなので、いま抱えている数を
     * 差し引いた残りを窓の上限にする。</p>
     */
    private static long getSafeOutputWindow(CraftingJobView view, IPatternDetails details) {
        long limit = Long.MAX_VALUE;
        for (GenericStack output : details.getOutputs()) {
            if (output.amount() <= 0L) {
                return 0L;
            }
            long headroom = Long.MAX_VALUE - heldForOutput(view, output.what());
            if (headroom <= 0L) {
                return 0L;
            }
            limit = Math.min(limit, headroom / output.amount());
        }
        return limit;
    }

    /**
     * この出力キーについてCPUがいずれ抱える数。
     *
     * <p>完成待ちのぶんは搬入されるとクラフト在庫へ足されるので、両方の合計を見る。
     * 大きいほうだけでは、在庫と完成待ちが両方大きいときに搬入後の在庫がlongを折り返す。</p>
     */
    private static long heldForOutput(CraftingJobView view, AEKey key) {
        long inventory = view.getInventory().extract(key, Long.MAX_VALUE, Actionable.SIMULATE);
        long waitingFor = view.getWaitingFor().extract(key, Long.MAX_VALUE, Actionable.SIMULATE);
        return SaturatingMath.add(Math.max(inventory, 0L), Math.max(waitingFor, 0L));
    }

    /** 端材、たとえばバケツの見込みを、実際に処理した回数ぶんに減らす。取り出しは回数の倍数なので割り切れる。 */
    private static void scaleContainerItems(KeyCounter containerItems, long times, long done) {
        KeyCounter scaled = new KeyCounter();
        for (var entry : containerItems) {
            scaled.add(entry.getKey(), entry.getLongValue() / times * done);
        }
        containerItems.reset();
        containerItems.addAll(scaled);
    }

    private static void ensureCanAdd(KeyCounter counter, AEKey key, long amount) {
        if (amount < 0L || counter.get(key) > Long.MAX_VALUE - amount) {
            throw new ArithmeticException("bulk crafting counter overflow for " + key);
        }
    }

    /** KeyCounterへ追加する前に個別キーのlong境界を検査する。 */
    private static void addExact(KeyCounter counter, AEKey key, long amount) {
        ensureCanAdd(counter, key, amount);
        counter.add(key, amount);
    }

    /**
     * {@link #extractInputs} の戻り値。
     *
     * @param inputs取り出した材料
     * @param times実際に取り出せた回数。要求した回数とは限らない
     */
    private record Extracted(KeyCounter[] inputs, KeyCounter networkInputs, long times) {
    }

    private static BulkCraftingHandler findBulkProvider(CraftingService craftingService, IPatternDetails details) {
        for (ICraftingProvider provider : craftingService.getProviders(details)) {
            BulkCraftingHandler bulk = BulkCraftingProviderAdapters.find(provider);
            if (bulk != null && !provider.isBusy() && bulk.getBulkCapacity(details) > 0) {
                return bulk;
            }
        }
        return null;
    }

    /**
     * まとめ処理を諦めて通常経路に戻したことを残す。
     *
     * <p>クラフト1回ごとに通り得るので、理由ごとに最初の1回だけWARN、
     * 以降はDEBUGに落とす。同じ理由で毎tick失敗するとログが埋まるため。</p>
     */
    private static void logFallback(String reason, IPatternDetails details) {
        if (LogOnce.first("bulk-crafting-fallback:" + reason)) {
            LOGGER.warn("InsaneAE: bulk crafting {} for {}, falling back (further occurrences are logged at DEBUG).",
                    reason, details.getDefinition());
        } else {
            LOGGER.debug("InsaneAE: bulk crafting {} for {}, falling back.", reason, details.getDefinition());
        }
    }
}
