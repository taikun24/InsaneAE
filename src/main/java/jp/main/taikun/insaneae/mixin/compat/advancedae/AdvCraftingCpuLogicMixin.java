package jp.main.taikun.insaneae.mixin.compat.advancedae;

import appeng.api.networking.energy.IEnergyService;
import appeng.me.service.CraftingService;
import jp.main.taikun.insaneae.quantum.BulkCraftingHook;
import jp.main.taikun.insaneae.quantum.ReflectiveCraftingJobView;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Advanced AEのQuantum Computerにも、まとめ処理の高速経路を通す。
 *
 * <h2>なぜ要るのか</h2>
 * <p>Advanced AEはAE2のクラフトCPUを使わず、{@code AdvCraftingCPULogic} という
 * 自前の複製を用意して、{@code CraftingService} へのMixin ({@code tickAdvClusters}) で
 * 自分でティックを回している。そのため{@code CraftingCpuLogicBulkMixin}はAE2用で、素通りされ、
 * Quantum CPUが1クラフトずつの遅い経路に落ちてしまう。
 * Advanced Quantum Engineeringのような「AAEの部品を強化する」アドオンも
 * 独自のCPU系統は作らないので、ここ1か所で全部まかなえる。</p>
 *
 * <h2>Advanced AEが入っていない環境では</h2>
 * <p>{@link Pseudo} が付いているので、対象クラスが見つからなければこのMixinだけが
 * 黙って読み飛ばされる。コンパイル依存もランタイム依存も要らない。
 * 登録先の {@code insaneae.optional.mixins.json} は {@code required=false} なので、
 * 注入に失敗してもゲームは起動する。まとめ処理が働かなくなるだけ。</p>
 *
 * <h2>他のModと先頭を取り合う件</h2>
 * <p>AE2 Crafting Optimizerも同じ {@code executeCrafting} の先頭に打ち切り付きで注入している。
 * Mixinは打ち切られた時点でメソッドを抜けるので、先に注入されたほうが勝ち、
 * もう一方は走らない。どちらが勝っても結果は正しく、負けたほうは何もしなかったことになる。
 * ずれが溜まらないよう帳簿は {@link BulkCraftingHook} 側で清算している。</p>
 *
 * <p>ジョブの中身へは {@link ReflectiveCraftingJobView} 経由で届く。AAEのジョブは
 * AE2のものと同名・同構造だが別のクラスなので、型を名指しできないため。</p>
 */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic", remap = false)
public abstract class AdvCraftingCpuLogicMixin {

    @Unique
    private BulkCraftingHook insaneae$hook;

    /**
     * Mixinのフィールド初期化子に頼らず、使うときに作る。
     * 対象クラスのコンストラクタが複数あっても確実に動くようにするため。
     */
    @Unique
    private BulkCraftingHook insaneae$hook() {
        if (insaneae$hook == null) {
            insaneae$hook = new BulkCraftingHook();
        }
        return insaneae$hook;
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
