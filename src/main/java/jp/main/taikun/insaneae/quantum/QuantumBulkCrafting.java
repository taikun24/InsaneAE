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
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.execution.InputTemplate;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import com.mojang.logging.LogUtils;
import jp.main.taikun.insaneae.mixin.ElapsedTimeTrackerInvoker;
import jp.main.taikun.insaneae.mixin.ExecutingCraftingJobAccessor;
import jp.main.taikun.insaneae.mixin.TaskProgressAccessor;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.Iterator;
import java.util.Map;

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
     * @return まとめ処理したクラフト回数。0 なら AE2 本来の処理を続行させること。
     */
    public static int execute(ExecutingCraftingJob job, ListCraftingInventory inventory,
            CraftingCPUCluster cluster, int maxPatterns,
            CraftingService craftingService, IEnergyService energyService, Level level) {
        if (job == null || maxPatterns <= 0) {
            return 0;
        }

        ExecutingCraftingJobAccessor jobAccess = (ExecutingCraftingJobAccessor) job;
        Map<Object, Object> tasks = jobAccess.insaneae$getTasks();
        int pushed = 0;

        Iterator<Map.Entry<Object, Object>> it = tasks.entrySet().iterator();
        while (it.hasNext() && pushed < maxPatterns) {
            Map.Entry<Object, Object> task = it.next();
            IPatternDetails details = (IPatternDetails) task.getKey();
            TaskProgressAccessor progress = (TaskProgressAccessor) task.getValue();
            long remaining = progress.insaneae$getValue();
            if (remaining <= 0) {
                it.remove();
                continue;
            }

            IBulkCraftingProvider provider = findBulkProvider(craftingService, details);
            if (provider == null) {
                continue;
            }
            long limit = Math.min(Math.min(remaining, provider.getBulkCapacity(details)), maxPatterns - pushed);
            if (limit <= 0) {
                continue;
            }

            long done = pushBulk(job, jobAccess, inventory, details, provider, limit, energyService, level);
            if (done > 0) {
                pushed += (int) done;
                long left = remaining - done;
                progress.insaneae$setValue(left);
                if (left <= 0) {
                    it.remove();
                }
                cluster.markDirty();
            }
        }

        return pushed;
    }

    /** 材料の取り出しから帳簿の更新まで。処理できた回数を返す。 */
    private static long pushBulk(ExecutingCraftingJob job, ExecutingCraftingJobAccessor jobAccess,
            ListCraftingInventory inventory, IPatternDetails details, IBulkCraftingProvider provider,
            long limit, IEnergyService energyService, Level level) {
        KeyCounter containerItems = new KeyCounter();

        // 電力が足りなければ回数を減らして 1 度だけやり直す。
        for (int attempt = 0; attempt < 2 && limit > 0; attempt++) {
            containerItems.reset();
            KeyCounter[] inputs = extractInputs(details, inventory, level, limit, containerItems);
            if (inputs == null) {
                return 0;
            }
            long times = limit;

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

            ListCraftingInventory waitingFor = jobAccess.insaneae$getWaitingFor();
            for (GenericStack output : details.getOutputs()) {
                waitingFor.insert(output.what(), output.amount() * done, Actionable.MODULATE);
            }
            ElapsedTimeTrackerInvoker tracker = (ElapsedTimeTrackerInvoker) jobAccess.insaneae$getTimeTracker();
            for (var entry : containerItems) {
                waitingFor.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
                tracker.insaneae$addMaxItems(entry.getLongValue(), entry.getKey().getType());
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
     * @return 取り出した材料。1 回ぶんも取れなければ null。
     */
    private static KeyCounter[] extractInputs(IPatternDetails details, ListCraftingInventory inventory,
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
        return holder;
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
