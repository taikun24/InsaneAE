package jp.main.taikun.insaneae.mixin.ui;

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
 * 自前の画面、つまりQuantum CPUと超特大インターフェイスが、他のAE2アドオンのせいで
 * 開けなくなるのを防ぐ。
 *
 * <p>AE2の {@code WidgetContainer.add(id, widget)} は必ず
 * {@link ScreenStyle#getWidget(String)} を引き、スタイルJSONに無いIDなら
 * {@code IllegalStateException}を投げる。テキストの方は寛容。
 * パターンプロバイダの画面にボタンを足すアドオンは、AE2本体の
 * {@code ae2:screens/pattern_provider.json} をリソースパックとして上書きしてIDを足す。
 * ところがこちらは自前のスタイル
 * ({@code ae2:screens/insaneae/*.json}) を使うので、
 * アドオンが {@code PatternProviderScreen} 等に足したウィジェットのIDがどこにも定義されておらず、
 * 画面を開いた瞬間に落ちる。devでは絶対に再現しない。</p>
 *
 * <p>そこでこちらのスタイルに限って、未定義のIDを例外ではなく
 * 「画面外に置いた空のウィジェット」として返す。アドオンのボタンは見えないが、
 * 少なくとも画面は開ける。どのIDが足りなかったかは1回だけ警告に出すので、
 * 必要ならスタイルJSONに位置を書いて正しく表示させられる。</p>
 *
 * <p>他の画面、つまりAE2本体や他Modのものは素の挙動のまま。判定は「このスタイルに
 * {@code nextPage} があるか」= 自前のページ送り付き画面かどうかで行う。</p>
 */
@Mixin(value = ScreenStyle.class, remap = false)
public abstract class ScreenStyleMixin {

    @Unique
    private static final Logger INSANEAE_LOG = LogUtils.getLogger();

    /** 同じIDで毎フレーム警告しないための記録。 */
    @Unique
    private static final Set<String> INSANEAE_REPORTED = new HashSet<>();

    /** 自前のスタイルだけを見分けるための目印。こちらのJSONにしかないID。 */
    @Unique
    private static final String INSANEAE_MARKER = "nextPage";

    @Shadow
    @Final
    private Map<String, WidgetStyle> widgets;

    @Inject(method = "getWidget", at = @At("HEAD"), cancellable = true, require = 0)
    private void insaneae$tolerateUnknownWidget(String id, CallbackInfoReturnable<WidgetStyle> cir) {
        if (widgets.containsKey(id) || !widgets.containsKey(INSANEAE_MARKER)) {
            return;
        }
        if (INSANEAE_REPORTED.add(id)) {
            INSANEAE_LOG.warn("InsaneAE: 自前の画面スタイルに widget \"{}\" が無い"
                    //? if >=1.21 {
                    + " (他の AE2 アドオンが AE2 の画面に足したもの)。"
                    //?} else {
                    /*+ " (他のAE2アドオンがPatternProviderScreenに足したもの)。"
                    *///?}
                    + " 画面外に逃がして表示だけ諦める。", id);
        }
        cir.setReturnValue(insaneae$offscreen());
    }

    /** 画面外・サイズ0のウィジェット枠。 */
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
