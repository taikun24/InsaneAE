package jp.main.taikun.insaneae.quantum;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.InputTemplate;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;
import com.mojang.logging.LogUtils;
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

    /** 材料の取り出しから帳簿の更新まで。処理できた回数を返す。 */
    private static long pushBulk(CraftingJobView view, IPatternDetails details,
            IBulkCraftingProvider provider, long limit, IEnergyService energyService, Level level) {
        ListCraftingInventory inventory = view.getInventory();
        KeyCounter containerItems = new KeyCounter();

        // 電力が足りなければ回数を減らして 1 度だけやり直す。
        for (int attempt = 0; attempt < 2 && limit > 0; attempt++) {
            containerItems.reset();
            Extracted extracted = extractInputs(details, inventory, level, limit, containerItems);
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
                    CraftingCpuHelper.reinjectPatternInputs(inventory, inputs);
                    limit = (long) (times * (available / power));
                    continue;
                }
                energyService.extractAEPower(power, Actionable.MODULATE, PowerMultiplier.CONFIG);
            }

            long done = provider.pushPatternBulk(details, inputs, times);
            if (done <= 0) {
                // 受け取ってもらえなかった: 電力は戻せないが材料は戻す。
                CraftingCpuHelper.reinjectPatternInputs(inventory, inputs);
                return 0;
            }

            ListCraftingInventory waitingFor = view.getWaitingFor();
            for (GenericStack output : details.getOutputs()) {
                waitingFor.insert(output.what(), output.amount() * done, Actionable.MODULATE);
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
    private static Extracted extractInputs(IPatternDetails details, ListCraftingInventory inventory,
            Level level, long times, KeyCounter containerItems) {
        IPatternDetails.IInput[] inputs = details.getInputs();

        // 1) 何回ぶん取れるか調べる (在庫を触らない)
        for (IPatternDetails.IInput input : inputs) {
            long perCraft = input.getMultiplier();
            long units = 0;
            for (InputTemplate template : CraftingCpuHelper.getValidItemTemplates(inventory, input, level)) {
                long available = inventory.extract(template.key(), Long.MAX_VALUE, Actionable.SIMULATE);
                long candidate = available / template.amount();
                if (candidate > 0) {
                    units = candidate;
                    break;
                }
            }
            times = Math.min(times, units / perCraft);
            if (times <= 0) {
                return null;
            }
        }

        // 2) 実際に取り出す
        KeyCounter[] holder = new KeyCounter[inputs.length];
        for (int x = 0; x < inputs.length; x++) {
            KeyCounter list = holder[x] = new KeyCounter();
            long needed = inputs[x].getMultiplier() * times;
            for (InputTemplate template : CraftingCpuHelper.getValidItemTemplates(inventory, inputs[x], level)) {
                long extracted = CraftingCpuHelper.extractTemplates(inventory, template, needed);
                if (extracted <= 0) {
                    continue;
                }
                list.add(template.key(), extracted * template.amount());
                AEKey containerItem = inputs[x].getRemainingKey(template.key());
                if (containerItem != null) {
                    containerItems.add(containerItem, extracted);
                }
                needed -= extracted;
                break; // 代替候補は 1 種類だけ (上記の理由)
            }
            if (needed > 0) {
                // 1) の見積もりと食い違った: 何も無かったことにして通常経路に任せる。
                LOGGER.warn("InsaneAE: bulk crafting could not gather ingredients for {}, falling back.",
                        details.getDefinition());
                CraftingCpuHelper.reinjectPatternInputs(inventory, holder);
                return null;
            }
        }
        return new Extracted(holder, times);
    }

    /**
     * {@link #extractInputs} の戻り値。
     *
     * @param inputs 取り出した材料
     * @param times  <b>実際に</b>取り出せた回数。要求した回数とは限らない
     */
    private record Extracted(KeyCounter[] inputs, long times) {
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
