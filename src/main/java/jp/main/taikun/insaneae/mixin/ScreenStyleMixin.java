package jp.main.taikun.insaneae.mixin;

import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.WidgetStyle;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 自前の画面 (Quantum CPU / 超特大インターフェイス) が、他の AE2 アドオンのせいで
 * 開けなくなるのを防ぐ。
 *
 * <p>AE2 の {@code WidgetContainer.add(id, widget)} は必ず
 * {@link ScreenStyle#getWidget(String)} を引き、<b>スタイル JSON に無い ID なら
 * {@code IllegalStateException} を投げる</b> (テキストの方は寛容)。
 * パターンプロバイダの画面にボタンを足すアドオンは、AE2 本体の
 * {@code ae2:screens/pattern_provider.json} をリソースパックとして上書きして ID を足す。
 * ところがこちらは<b>自前のスタイル</b>
 * ({@code ae2:screens/insaneae/*.json}) を使うので、
 * アドオンが {@code PatternProviderScreen} 等に足したウィジェットの ID がどこにも定義されておらず、
 * 画面を開いた瞬間に落ちる (= dev では絶対に再現しない)。</p>
 *
 * <p>そこで<b>こちらのスタイルに限って</b>、未定義の ID を例外ではなく
 * 「画面外に置いた空のウィジェット」として返す。アドオンのボタンは見えないが、
 * 少なくとも画面は開ける。どの ID が足りなかったかは 1 回だけ警告に出すので、
 * 必要ならスタイル JSON に位置を書いて正しく表示させられる。</p>
 *
 * <p>他の画面 (AE2 本体や他 Mod のもの) は素の挙動のまま。判定は「このスタイルに
 * {@code nextPage} があるか」= 自前のページ送り付き画面かどうかで行う。</p>
 */
@Mixin(value = ScreenStyle.class, remap = false)
public abstract class ScreenStyleMixin {

    @Unique
    private static final Logger INSANEAE_LOG = LogUtils.getLogger();

    /** 同じ ID で毎フレーム警告しないための記録。 */
    @Unique
    private static final Set<String> INSANEAE_REPORTED = new HashSet<>();

    /** 自前のスタイルだけを見分けるための目印 (こちらの JSON にしかない ID)。 */
    @Unique
    private static final String INSANEAE_MARKER = "nextPage";

    @Shadow
    @Final
    private Map<String, WidgetStyle> widgets;

    @Inject(method = "getWidget", at = @At("HEAD"), cancellable = true, require = 0)
    private void insaneae$tolerateUnknownWidget(String id, CallbackInfoReturnable<WidgetStyle> cir) {
        if (widgets.containsKey(id) || !widgets.containsKey(INSANEAE_MARKER)) {
            return;   // 定義済み、または自前以外の画面 → AE2 の挙動のまま
        }
        if (INSANEAE_REPORTED.add(id)) {
            INSANEAE_LOG.warn("InsaneAE: 自前の画面スタイルに widget \"{}\" が無い"
                    + " (他の AE2 アドオンが AE2 の画面に足したもの)。"
                    + " 画面外に逃がして表示だけ諦める。", id);
        }
        cir.setReturnValue(insaneae$offscreen());
    }

    /** 画面外・サイズ 0 のウィジェット枠。 */
    @Unique
    private static WidgetStyle insaneae$offscreen() {
        WidgetStyle style = new WidgetStyle();
        style.setLeft(-1000);
        style.setTop(-1000);
        style.setWidth(0);
        style.setHeight(0);
        return style;
    }
}
