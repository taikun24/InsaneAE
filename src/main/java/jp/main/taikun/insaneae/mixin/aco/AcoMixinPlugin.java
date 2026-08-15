package jp.main.taikun.insaneae.mixin.aco;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * ACO が入っているときだけ {@code insaneae.aco.mixins.json} を適用する。
 *
 * <h2>なぜ要るのか</h2>
 * <p>この設定に並ぶ Mixin は、うちのクラスに<b>ACO のインターフェイスを実装させる</b>もの
 * ({@code CraftingTableBatchTarget} 等)。反射では代用できないので Mixin 側が ACO の型を直接参照する。
 * ACO が無い環境でそのまま適用しようとすると、存在しないインターフェイスを実装した
 * クラスができあがって<b>検証に失敗する</b> — つまり Quantum CPU がロードできなくなる。</p>
 *
 * <p>そこで、ACO のクラスが読めるかどうかで適用可否を判定する。
 * {@code ModList} はこの段階ではまだ使えないので、クラスの有無で見る。</p>
 */
public class AcoMixinPlugin implements IMixinConfigPlugin {

    /**
     * 目印にするクラス。ここが読めなければ ACO は居ない (か古すぎる)。
     *
     * <p><b>実際に実装するインターフェイスそのものを見ること。</b>
     * {@code api.contract} を目印にすると 1.20.1 版の ACO では常に「居ない」になる —
     * あちらには contract パッケージがまだ入っていない (1.5.19 時点)。</p>
     */
    private static final String ACO_MARKER =
            "com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchTarget";

    private boolean acoPresent;

    @Override
    public void onLoad(String mixinPackage) {
        acoPresent = insaneae$canSee(ACO_MARKER);
    }

    /**
     * ACO のクラスが見えるか。
     *
     * <p><b>{@code Class.forName} だけでは足りない。</b>Forge 1.20.1 の Mixin フェーズでは
     * 他 Mod のクラスがまだこのクラスローダから見えず、常に「居ない」と判定してしまう
     * (NeoForge 1.21.1 では見えるので、片方だけ黙って連携が無効になる)。
     * Mixin サービスのバイトコード提供者なら、その段階でも読めるかどうかを答えられる。</p>
     */
    private static boolean insaneae$canSee(String className) {
        try {
            MixinService.getService().getBytecodeProvider().getClassNode(className.replace('.', '/'));
            return true;
        } catch (ClassNotFoundException | IOException | RuntimeException | LinkageError absent) {
            // 読めなければ Class.forName も試す (サービスの実装によっては前者が使えない)。
            try {
                Class.forName(className, false, AcoMixinPlugin.class.getClassLoader());
                return true;
            } catch (ClassNotFoundException | LinkageError stillAbsent) {
                return false;
            }
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return acoPresent;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
            IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
            IMixinInfo mixinInfo) {
    }
}
