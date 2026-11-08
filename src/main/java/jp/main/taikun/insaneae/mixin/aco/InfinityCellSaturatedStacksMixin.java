package jp.main.taikun.insaneae.mixin.aco;

import appeng.api.stacks.KeyCounter;
import jp.main.taikun.insaneae.integration.aco.InfiniteCellExactState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ME Infinity CellがAE2に申告する在庫を、longの天井 ({@code Long.MAX_VALUE}) まで引き上げる。
 *
 * <p>ACO 2.0は、AE2側の在庫がlongの天井に張り付いていないとBigInteger計画に入らない。
 * このセルは中身が無限なのに {@code Integer.MAX_VALUE} 個しか申告しないので、
 * BigIntegerの在庫 ({@link InfinityCellExactAmountProviderMixin}) を名乗っていても
 * AE2のlong計画のまま「材料不足」で終わる。ACO 1.5系はAE2のネットワーク在庫の読み取りを
 * 自分で差し替えて天井まで引き上げていたが、2.0でそのMixinが無くなった。</p>
 *
 * <p>同じキーを他のセルも申告していると {@code KeyCounter} の足し算が負へ折り返すので、
 * 天井を越えない分だけ足す。{@code InsaneCreativeCellInventory}と同じ。</p>
 */
@Pseudo
@Mixin(targets = {
        "net.yxiao233.meinfinitycell.common.inventory.InfinityCellInventory",
        "net.yxiao233.meinfinitycell.common.inventory.InfinitiesCellInventory"
}, remap = false)
public abstract class InfinityCellSaturatedStacksMixin {

    @Inject(method = "getAvailableStacks", at = @At("RETURN"), require = 0)
    private void insaneae$saturate(KeyCounter out, CallbackInfo ci) {
        InfiniteCellExactState state = ((InfiniteCellExactState.Holder) this).insaneae$infiniteExactState();
        if (state == null) {
            return;
        }
        for (var key : state.amounts().keySet()) {
            long headroom = Long.MAX_VALUE - Math.max(out.get(key), 0L);
            if (headroom > 0L) {
                out.add(key, headroom);
            }
        }
    }
}
