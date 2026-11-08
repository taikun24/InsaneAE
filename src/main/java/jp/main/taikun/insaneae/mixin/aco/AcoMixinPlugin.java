package jp.main.taikun.insaneae.mixin.aco;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * ACOが入っているときだけ {@code insaneae.aco.mixins.json} を適用する。
 *
 * <h2>なぜ要るのか</h2>
 * <p>この設定に並ぶMixinは、うちのクラスにACOのインターフェイスを実装させるもの
 * {@code CraftingTableBatchTarget}などの型。反射では代用できないので、Mixin側がACOの型を直接参照する。
 * ACOが無い環境でそのまま適用しようとすると、存在しないインターフェイスを実装した
 * クラスができあがって検証に失敗する — つまりQuantum CPUがロードできなくなる。</p>
 *
 * <p>そこで、ACOのクラスが読めるかどうかで適用可否を判定する。
 * {@code ModList} はこの段階ではまだ使えないので、クラスの有無で見る。</p>
 */
public class AcoMixinPlugin implements IMixinConfigPlugin {

    /**
     * 目印にするクラス。ここが読めなければACOは居ないか、古すぎる。
     *
     * <p>実際に実装するインターフェイスそのものを見ること。
     * {@code api.contract} を目印にすると1.20.1版のACOでは常に「居ない」になる —
     * あちらにはcontractパッケージがまだ入っていない。1.5.19時点の話。</p>
     */
    private static final String ACO_MARKER =
            "com.syaru.ae2craftingoptimizer.api.craftingtable.CraftingTableBatchTarget";

    /**
     * 在庫をBigIntegerで名乗る公開契約。ACO 1.5.20で入ったので、
     * それより古いACOでは目印のクラスがあってもこれは無い。
     * これを実装するMixinだけは、実在を別に確かめてから当てる
     * 無いまま当てると、存在しないインターフェイスを実装したクラスとして
     * 検証に失敗し、セルごとロードできなくなる)。
     */
    private static final String ACO_EXACT_STORAGE_PROVIDER =
            "com.syaru.ae2craftingoptimizer.api.contract.ExactStorageAmountProvider";

    /** 上のクラスを実装するMixin。ここだけ追加の条件が要る。 */
    private static final Set<String> EXACT_STORAGE_PROVIDER_MIXINS = Set.of(
            "jp.main.taikun.insaneae.mixin.aco.UltraCreativeCellExactAmountProviderMixin",
            "jp.main.taikun.insaneae.mixin.aco.InfinityCellExactAmountProviderMixin");

    /**
     * exact実行の納品先ポリシー。ACO 1.5.23で入った公開契約なので、
     * これを実装するMixinにも同じ形の追加条件が要る。
     */
    private static final String ACO_EXACT_INSERT_POLICY =
            "com.syaru.ae2craftingoptimizer.api.vector.ExactVectorStoragePolicy";

    /** 上のクラスを実装するMixin。 */
    private static final Set<String> EXACT_INSERT_POLICY_MIXINS = Set.of(
            "jp.main.taikun.insaneae.mixin.aco.UltraCreativeCellExactInsertPolicyMixin",
            "jp.main.taikun.insaneae.mixin.aco.InfinityCellExactInsertPolicyMixin");

    private boolean acoPresent;
    private boolean exactStorageProviderPresent;
    private boolean exactInsertPolicyPresent;

    @Override
    public void onLoad(String mixinPackage) {
        acoPresent = insaneae$canSee(ACO_MARKER);
        exactStorageProviderPresent = acoPresent && insaneae$canSee(ACO_EXACT_STORAGE_PROVIDER);
        exactInsertPolicyPresent = acoPresent && insaneae$canSee(ACO_EXACT_INSERT_POLICY);
    }

    /**
     * ACOのクラスが見えるか。
     *
     * <p>{@code Class.forName} だけでは足りない。Forge 1.20.1のMixinフェーズでは
     * 他Modのクラスがまだこのクラスローダから見えず、常に「居ない」と判定してしまう
     * NeoForge 1.21.1では見えるので、片方だけ黙って連携が無効になる。
     * Mixinサービスのバイトコード提供者なら、その段階でも読めるかどうかを答えられる。</p>
     */
    private static boolean insaneae$canSee(String className) {
        try {
            MixinService.getService().getBytecodeProvider().getClassNode(className.replace('.', '/'));
            return true;
        } catch (ClassNotFoundException | IOException | RuntimeException | LinkageError absent) {
            // 読めなければClass.forNameも試す。サービスの実装によっては前者が使えないため
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
        if (EXACT_STORAGE_PROVIDER_MIXINS.contains(mixinClassName)) {
            return exactStorageProviderPresent;
        }
        if (EXACT_INSERT_POLICY_MIXINS.contains(mixinClassName)) {
            return exactInsertPolicyPresent;
        }
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
