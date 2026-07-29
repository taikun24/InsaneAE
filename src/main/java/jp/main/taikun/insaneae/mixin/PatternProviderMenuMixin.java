package jp.main.taikun.insaneae.mixin;

import appeng.api.inventories.InternalInventory;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.menu.implementations.PatternProviderMenu;
import jp.main.taikun.insaneae.config.InsaneAEConfig;
import jp.main.taikun.insaneae.menu.QuantumCpuMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Quantum CPU の画面を開いたとき、パターン枠を<b>1 ページぶんだけ</b>スロットにする。
 *
 * <p>{@code PatternProviderMenu} のコンストラクタは
 * {@code logic.getPatternInv()} の枠数だけスロットを並べる。Quantum CPU は 1620 枠あるので、
 * そのままだとバニラの毎 tick の同期処理が 1620 枠ぶん走る
 * ({@link jp.main.taikun.insaneae.menu.PagedPatternInventory} の説明を参照)。
 * ここでコンストラクタが見るインベントリだけを<b>ページの窓</b>にすり替える。</p>
 *
 * <p>すり替えるのは Quantum CPU の画面のときだけで、AE2 本来のパターンプロバイダには触らない。
 * 注入に失敗した場合 ({@code require = 0}) は今までどおり全枠がスロットになるだけで、
 * {@code QuantumCpuScreen} 側がその状態も面倒を見る (クライアント側でページ送りする旧挙動)。</p>
 */
@Mixin(value = PatternProviderMenu.class, remap = false, priority = 1500)
public abstract class PatternProviderMenuMixin {

    // コンストラクタは 2 つあり、スロットを並べているのは MenuType を取る方 (もう一方はそれに委譲するだけ)。
    // 引数無しの "<init>" だと片方しか見に行かないので、記述子まで書く。
    @Redirect(method = "<init>(Lnet/minecraft/world/inventory/MenuType;IL"
            + "net/minecraft/world/entity/player/Inventory;L"
            + "appeng/helpers/patternprovider/PatternProviderLogicHost;)V",
            at = @At(value = "INVOKE",
                    target = "Lappeng/helpers/patternprovider/PatternProviderLogic;getPatternInv()"
                            + "Lappeng/api/inventories/InternalInventory;"),
            require = 0)
    private InternalInventory insaneae$pagePatternSlots(PatternProviderLogic logic) {
        InternalInventory patternInv = logic.getPatternInv();
        if ((Object) this instanceof QuantumCpuMenu menu && InsaneAEConfig.serverSidePatternPaging()) {
            return menu.insaneae$createPatternWindow(patternInv);
        }
        return patternInv;
    }
}
