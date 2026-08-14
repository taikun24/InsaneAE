package jp.main.taikun.insaneae.mixin.compat;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;
import java.util.Optional;
import jp.main.taikun.insaneae.integration.aco.AcoBigIntegerJobRegistry;
import jp.main.taikun.insaneae.integration.aco.AcoBigIntegerPlanBridge;
import jp.main.taikun.insaneae.quantum.BulkCraftingHook;
import jp.main.taikun.insaneae.quantum.ReflectiveCraftingJobView;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Advanced AE の Quantum Computer にも、まとめ処理の高速経路を通す。
 *
 * <h2>なぜ要るのか</h2>
 * <p>Advanced AE は AE2 のクラフト CPU を使わず、{@code AdvCraftingCPULogic} という
 * <b>自前の複製</b>を用意して、{@code CraftingService} への Mixin ({@code tickAdvClusters}) で
 * 自分でティックを回している。そのため {@code CraftingCpuLogicMixin} (AE2 用) は素通りされ、
 * Quantum CPU が<b>1 クラフトずつの遅い経路</b>に落ちていた。
 * Advanced Quantum Engineering のような「AAE の部品を強化する」アドオンも
 * 独自の CPU 系統は作らないので、ここ 1 か所で全部まかなえる。</p>
 *
 * <h2>Advanced AE が入っていない環境では</h2>
 * <p>{@link Pseudo} が付いているので、対象クラスが見つからなければ<b>この Mixin だけが
 * 黙って読み飛ばされる</b>。コンパイル依存もランタイム依存も要らない。
 * 登録先の {@code insaneae.compat.mixins.json} は {@code required=false} なので、
 * 注入に失敗してもゲームは起動する (まとめ処理が効かなくなるだけ)。</p>
 *
 * <h2>他の Mod と先頭を取り合う件</h2>
 * <p>AE2 Crafting Optimizer も同じ {@code executeCrafting} の先頭に打ち切り付きで注入している。
 * Mixin は打ち切られた時点でメソッドを抜けるので、<b>先に注入されたほうが勝ち、
 * もう一方は走らない</b>。どちらが勝っても結果は正しく、負けたほうは何もしなかったことになる。
 * ずれが溜まらないよう帳簿は {@link BulkCraftingHook} 側で清算している。</p>
 *
 * <p>ジョブの中身へは {@link ReflectiveCraftingJobView} 経由で届く。AAE のジョブは
 * AE2 のものと同名・同構造だが<b>別のクラス</b>なので、型を名指しできないため。</p>
 */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic", remap = false)
public abstract class AdvCraftingCpuLogicMixin {

    @Unique
    private BulkCraftingHook insaneae$hook;

    /**
     * Mixin のフィールド初期化子に頼らず、使うときに作る。
     * (対象クラスのコンストラクタが複数あっても確実に動くようにするため。)
     */
    @Unique
    private BulkCraftingHook insaneae$hook() {
        if (insaneae$hook == null) {
            insaneae$hook = new BulkCraftingHook();
        }
        return insaneae$hook;
    }

    /** ACOのexact計画を、この複製CPU自身が所有するjobへ渡す。 */
    @Inject(method = "trySubmitJob", at = @At("HEAD"), cancellable = true, require = 0)
    private void insaneae$inspectAcoPlan(
            IGrid grid,
            ICraftingPlan plan,
            IActionSource source,
            ICraftingRequester requester,
            CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        Optional<AcoBigIntegerPlanBridge.Plan> exact = AcoBigIntegerPlanBridge.inspect(plan);
        // ACOのsidecarが無い通常計画は、Advanced AE本来の提出経路へ渡す。
        if (exact.isEmpty()) {
            return;
        }
        // Quantum CPUが扱えない混成計画は、飽和longへ変換せず明示的に拒否する。
        if (!AcoBigIntegerPlanBridge.supportsQuantumCpu(exact.get(), grid)) {
            cir.setReturnValue(CraftingSubmitResult.INCOMPLETE_PLAN);
        }
    }

    /** exact計画だけはAdvanced AEのlong初期搬入を止め、窓ごとに材料を取得する。 */
    @Redirect(
            method = "trySubmitJob",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/crafting/execution/CraftingCpuHelper;tryExtractInitialItems(Lappeng/api/networking/crafting/ICraftingPlan;Lappeng/api/networking/IGrid;Lappeng/crafting/inv/ListCraftingInventory;Lappeng/api/networking/security/IActionSource;)Lappeng/api/stacks/GenericStack;"),
            require = 0)
    private GenericStack insaneae$skipAcoInitialItems(
            ICraftingPlan plan,
            IGrid grid,
            ListCraftingInventory inventory,
            IActionSource source) {
        Optional<AcoBigIntegerPlanBridge.Plan> exact = AcoBigIntegerPlanBridge.inspect(plan);
        // exact計画だけを窓処理へ渡し、通常計画はAE2の初期搬入をそのまま使う。
        if (exact.isPresent() && AcoBigIntegerPlanBridge.supportsQuantumCpu(exact.get(), grid)) {
            return null;
        }
        return CraftingCpuHelper.tryExtractInitialItems(plan, grid, inventory, source);
    }

    /** 成功したAdvanced AE jobへexact task台帳を結び付ける。 */
    @Inject(method = "trySubmitJob", at = @At("RETURN"), require = 0)
    private void insaneae$installAcoPlan(
            IGrid grid,
            ICraftingPlan plan,
            IActionSource source,
            ICraftingRequester requester,
            CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        Optional<AcoBigIntegerPlanBridge.Plan> exact = AcoBigIntegerPlanBridge.inspect(plan);
        // 失敗した提出や通常jobには、古いexact台帳を作らない。
        if (exact.isEmpty() || cir.getReturnValue() == null
                || !cir.getReturnValue().successful()) {
            return;
        }
        Object job = ReflectiveCraftingJobView.jobOwner(this);
        // private jobを解決できない版では、標準Advanced AE経路へ戻して状態を捏造しない。
        if (job == null) {
            return;
        }
        AcoBigIntegerJobRegistry.install(job, exact.get());
    }

    /** CPU破棄時にexact台帳を再利用させない。 */
    @Inject(method = "cancel", at = @At("HEAD"), require = 0)
    private void insaneae$removeAcoPlanOnCancel(CallbackInfo ci) {
        Object job = ReflectiveCraftingJobView.jobOwner(this);
        // 現在のjobだけを削除し、次の提出状態には触れない。
        if (job != null) {
            AcoBigIntegerJobRegistry.remove(job);
        }
    }

    /** 正常終了時も未消費のexact台帳を残さない。 */
    @Inject(method = "finishJob", at = @At("HEAD"), require = 0)
    private void insaneae$removeAcoPlanOnFinish(boolean success, CallbackInfo ci) {
        Object job = ReflectiveCraftingJobView.jobOwner(this);
        // 成功・失敗のどちらでもAdvanced AEがjob所有権を閉じるため台帳も閉じる。
        if (job != null) {
            AcoBigIntegerJobRegistry.remove(job);
        }
    }

    @Inject(method = "executeCrafting", at = @At("HEAD"), cancellable = true, require = 0)
    private void insaneae$bulkCrafting(int maxPatterns, CraftingService craftingService,
            IEnergyService energyService, Level level, CallbackInfoReturnable<Integer> cir) {
        if (cir.isCancelled()) {
            return;
        }
        int finished = insaneae$hook().begin(ReflectiveCraftingJobView.of(this),
                maxPatterns, craftingService, energyService, level);
        if (finished >= 0) {
            cir.setReturnValue(finished);
        }
    }

    @ModifyVariable(method = "executeCrafting", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private int insaneae$reduceBudget(int maxPatterns) {
        return insaneae$hook().reduce(maxPatterns);
    }

    @Inject(method = "executeCrafting", at = @At("RETURN"), cancellable = true, require = 0)
    private void insaneae$addBulkToResult(int maxPatterns, CraftingService craftingService,
            IEnergyService energyService, Level level, CallbackInfoReturnable<Integer> cir) {
        int bulk = insaneae$hook().takePushed();
        if (bulk > 0) {
            cir.setReturnValue(cir.getReturnValue() + bulk);
        }
    }
}
