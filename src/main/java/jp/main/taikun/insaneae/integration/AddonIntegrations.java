package jp.main.taikun.insaneae.integration;

import com.mojang.logging.LogUtils;
import jp.main.taikun.insaneae.integration.advancedae.AdvancedAeIntegration;
import jp.main.taikun.insaneae.integration.appflux.AppFluxIntegration;
import jp.main.taikun.insaneae.integration.appmek.AppMekIntegration;
import jp.main.taikun.insaneae.integration.extendedae.ExtendedAeIntegration;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 対応している AE2 アドオンの名簿と、そのうち実際に入っているものの一覧。
 *
 * <p><b>生成はラムダの中に閉じ込めること</b> ({@code AppFluxIntegration::new} ではなく
 * {@code () -> new AppFluxIntegration()})。メソッド参照は名簿を作った時点で参照先クラスを
 * 解決するので、相手 Mod が居ない環境でその型を引きにいって落ちる。ラムダの本体なら
 * 実行されるまでクラスはロードされない。</p>
 */
public final class AddonIntegrations {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 対応先の 1 行。{@code feature} はログに出すだけの説明。 */
    private record Known(String modId, String feature, Supplier<AddonIntegration> factory) {
    }

    private static final List<Known> KNOWN = List.of(
            new Known(AppMekIntegration.MODID, "chemical storage cells",
                    () -> new AppMekIntegration()),
            new Known(AppFluxIntegration.MODID, "FE storage cells",
                    () -> new AppFluxIntegration()),
            new Known(ExtendedAeIntegration.MODID, "acceleration cards on ExtendedAE machines",
                    () -> new ExtendedAeIntegration()),
            new Known(AdvancedAeIntegration.MODID, "acceleration cards on AdvancedAE machines",
                    () -> new AdvancedAeIntegration()));

    private static List<AddonIntegration> active;

    private AddonIntegrations() {
    }

    /**
     * Mod 構築時に 1 回だけ呼ぶ。入っているアドオンの連携を作り、アイテムを登録させる。
     * <b>{@code ModCells.register(bus)} より前</b>に呼ぶこと (セルは ModCells の
     * DeferredRegister に相乗りしているので、バスに繋ぐ前に足し終える必要がある)。
     */
    public static void init() {
        if (active != null) {
            throw new IllegalStateException("AddonIntegrations.init() called twice");
        }
        List<AddonIntegration> found = new ArrayList<>();
        for (Known known : KNOWN) {
            if (!ModList.get().isLoaded(known.modId())) {
                continue;
            }
            LOGGER.info("InsaneAE: {} detected, enabling {}.", known.modId(), known.feature());
            AddonIntegration integration = known.factory().get();
            integration.registerContent();
            found.add(integration);
        }
        active = List.copyOf(found);
    }

    /** 実際に入っているアドオンの連携。 */
    public static List<AddonIntegration> active() {
        if (active == null) {
            throw new IllegalStateException("AddonIntegrations.init() has not run yet");
        }
        return active;
    }

    /** 名簿に載っているが入っていないアドオン (datagen で「レシピが欠ける」警告を出す用)。 */
    public static List<String> missingModIds() {
        return KNOWN.stream()
                .map(Known::modId)
                .filter(modId -> !ModList.get().isLoaded(modId))
                .toList();
    }
}
