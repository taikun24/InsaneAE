package jp.main.taikun.insaneae.mixin.cpu;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.mojang.logging.LogUtils;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import jp.main.taikun.insaneae.integration.aco.AcoBigIntegerJobRegistry;
import jp.main.taikun.insaneae.integration.aco.AcoBigIntegerPlanBridge;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ACO の BigInteger 計画をクラフト CPU に載せる・保存する・片付ける処理。
 *
 * <p>まとめ処理 ({@code executeCrafting}) は ACO の注入より先に走らせる必要があるので、
 * 優先度を変えた {@link CraftingCpuLogicBulkMixin} に分けてある。</p>
 */
@Mixin(value = CraftingCpuLogic.class, remap = false)
public abstract class CraftingCpuLogicMixin {

    @Shadow
    private ExecutingCraftingJob job;

    @Shadow
    @Final
    CraftingCPUCluster cluster;

    @Shadow
    public abstract ListCraftingInventory getInventory();

    /**
     * ACOのBigInteger計画を、飽和したAE2 long計画として通常実行しない。
     * 対応できない加工Patternは、このCPUへ曖昧に渡さずAE2の明示的な失敗へ戻す。
     */
    @Inject(method = "trySubmitJob", at = @At("HEAD"), cancellable = true)
    private void insaneae$inspectAcoPlan(
            IGrid grid,
            ICraftingPlan plan,
            IActionSource source,
            ICraftingRequester requester,
            CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        Optional<AcoBigIntegerPlanBridge.Plan> exact = AcoBigIntegerPlanBridge.inspect(plan);
        if (exact.isEmpty()) {
            return;
        }
        AcoBigIntegerPlanBridge.Plan candidate = exact.get();
        if (!AcoBigIntegerPlanBridge.supportsQuantumCpu(candidate, grid)) {
            // Quantum CPUが実行できるのは作業台Patternだけなので、混在Jobを推測実行しない。
            cir.setReturnValue(CraftingSubmitResult.INCOMPLETE_PLAN);
            return;
        }
    }

    /**
     * ACO Exact planだけはAE2のlong初期一括搬入を止める。
     * 実行窓ごとのME取り出しはQuantumBulkCraftingが原子的に担当する。
     */
    @Redirect(
            method = "trySubmitJob",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/crafting/execution/CraftingCpuHelper;tryExtractInitialItems(Lappeng/api/networking/crafting/ICraftingPlan;Lappeng/api/networking/IGrid;Lappeng/crafting/inv/ListCraftingInventory;Lappeng/api/networking/security/IActionSource;)Lappeng/api/stacks/GenericStack;"))
    private GenericStack insaneae$skipAcoInitialItems(
            ICraftingPlan plan,
            IGrid grid,
            ListCraftingInventory inventory,
            IActionSource source) {
        Optional<AcoBigIntegerPlanBridge.Plan> exact = AcoBigIntegerPlanBridge.inspect(plan);
        if (exact.isPresent() && AcoBigIntegerPlanBridge.supportsQuantumCpu(exact.get(), grid)) {
            return null;
        }
        return CraftingCpuHelper.tryExtractInitialItems(plan, grid, inventory, source);
    }

    /** 成功したAE2 JobとExact task台帳を同じオーナーへ結び付ける。 */
    @Inject(method = "trySubmitJob", at = @At("RETURN"))
    private void insaneae$installAcoPlan(
            IGrid grid,
            ICraftingPlan plan,
            IActionSource source,
            ICraftingRequester requester,
            CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        Optional<AcoBigIntegerPlanBridge.Plan> exact = AcoBigIntegerPlanBridge.inspect(plan);
        if (exact.isEmpty() || cir.getReturnValue() == null || !cir.getReturnValue().successful()
                || job == null) {
            return;
        }
        AcoBigIntegerJobRegistry.install(job, exact.get());
    }

    /** キャンセル時に、未完了のExact台帳を次のJobへ持ち越さない。 */
    @Inject(method = "cancel", at = @At("HEAD"))
    private void insaneae$removeAcoPlanOnCancel(CallbackInfo ci) {
        if (job != null) {
            AcoBigIntegerJobRegistry.remove(job);
        }
    }

    /** 正常終了時も、カーソルが最後のTaskを消せなかった場合の残留台帳を掃除する。 */
    @Inject(method = "finishJob", at = @At("HEAD"))
    private void insaneae$removeAcoPlanOnFinish(boolean success, CallbackInfo ci) {
        if (job != null) {
            // 成功・失敗のどちらでもAE2がJob所有権を閉じるため、Exact台帳も同時に閉じる。
            AcoBigIntegerJobRegistry.remove(job);
        }
    }

    @Unique
    private static final Logger INSANEAE$EXACT_LOGGER = LogUtils.getLogger();

    /** Exact台帳のBigInteger残数を保存するタグ名。 */
    @Unique
    private static final String INSANEAE$EXACT_TASKS_TAG = "insaneae_exact_tasks";

    /**
     * Exact台帳の残数をクラスタNBTへ相乗り保存する。
     *
     * <p>台帳はメモリ上のWeakHashMapにしか無いので、保存しないと再起動でここだけが消え、
     * AE2側に残った飽和longタスクが素で実行されてしまう (材料不足・二重計上・再注文)。
     * PatternはAE2のタスク保存と同じく定義アイテム (AEItemKey) で表す。</p>
     */
    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void insaneae$saveExactTasks(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        if (job == null) {
            return;
        }
        AcoBigIntegerJobRegistry.snapshot(job).ifPresent(snapshot -> {
            ListTag list = new ListTag();
            snapshot.forEach((pattern, remaining) -> {
                CompoundTag entry = new CompoundTag();
                entry.put("pattern", pattern.getDefinition().toTag(registries));
                // longに収まらない値があるため、残数は10進文字列で保存する。
                entry.putString("remaining", remaining.toString());
                list.add(entry);
            });
            tag.put(INSANEAE$EXACT_TASKS_TAG, list);
        });
    }

    /**
     * 復元したJobへExact台帳を結び直す。
     *
     * <p>保存済みのPatternは、AE2が同じNBTから復元したタスクの定義アイテムと突き合わせて
     * 同一のIPatternDetailsインスタンスへ解決する。タスク側に無いPattern (パターンが
     * 消された等) は、AE2側の該当タスクも消えているので黙って捨ててよい。</p>
     */
    @Inject(method = "readFromNBT", at = @At("RETURN"))
    private void insaneae$loadExactTasks(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        if (job == null || !tag.contains(INSANEAE$EXACT_TASKS_TAG, Tag.TAG_LIST)) {
            return;
        }
        Map<AEItemKey, IPatternDetails> byDefinition = new HashMap<>();
        for (Object key : ((ExecutingCraftingJobAccessor) (Object) job).insaneae$getTasks().keySet()) {
            if (key instanceof IPatternDetails details) {
                byDefinition.put(details.getDefinition(), details);
            }
        }
        Map<IPatternDetails, BigInteger> restored = new LinkedHashMap<>();
        for (Tag element : tag.getList(INSANEAE$EXACT_TASKS_TAG, Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            try {
                AEItemKey definition = AEItemKey.fromTag(registries, entry.getCompound("pattern"));
                IPatternDetails details = definition == null ? null : byDefinition.get(definition);
                if (details == null) {
                    INSANEAE$EXACT_LOGGER.warn(
                            "InsaneAE: dropping a saved exact crafting task whose pattern no longer exists: {}",
                            entry.getCompound("pattern"));
                    continue;
                }
                restored.put(details, new BigInteger(entry.getString("remaining")));
            } catch (RuntimeException corrupt) {
                // 壊れた保存データ1件でJob全体の復元を巻き込まない (パターン欠落・数値破損とも)。
                INSANEAE$EXACT_LOGGER.warn(
                        "InsaneAE: dropping a corrupt saved exact crafting task", corrupt);
            }
        }
        if (!restored.isEmpty()) {
            AcoBigIntegerJobRegistry.install(job, restored);
        }
    }
}
