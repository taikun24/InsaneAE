package jp.main.taikun.insaneae.mixin.aco;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
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

    /** 公開連携境界の入口。ここが読めなければ ACO は居ない (か古すぎる)。 */
    private static final String ACO_MARKER =
            "com.syaru.ae2craftingoptimizer.api.contract.IntegrationCapabilitiesRegistry";

    private boolean acoPresent;

    @Override
    public void onLoad(String mixinPackage) {
        try {
            Class.forName(ACO_MARKER, false, AcoMixinPlugin.class.getClassLoader());
            acoPresent = true;
        } catch (ClassNotFoundException | LinkageError absent) {
            acoPresent = false;
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
