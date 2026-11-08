package jp.main.taikun.insaneae.integration.astral;

import appeng.api.stacks.AEKey;
import mekanism.api.chemical.ChemicalStack;
import jp.main.taikun.insaneae.integration.appmek.AppMekIntegration;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * 化学物質、つまりガス、注入型、顔料、スラリーをAE2のキーに変換する。
 *
 * <p>ネットワークが化学物質を持てるのはApplied Mekanisticsがあるときだけなので、
 * appmekの型に触るのはこのクラスの中だけに閉じ込めてある
 * 未導入の環境で{@code MekanismKey}を読みに行って落ちないように、
 * 分岐してから初めてクラスをロードする)。</p>
 */
public final class AstralChemicalKey {

    private static final boolean APPMEK_LOADED = ModList.get().isLoaded(AppMekIntegration.MODID);

    private AstralChemicalKey() {
    }

    /** appmekが無い、または変換できない中身ならnull。Mekanism本来の搬出に任せる。 */
    @Nullable
    public static AEKey of(@Nullable ChemicalStack<?> stack) {
        if (!APPMEK_LOADED || stack == null || stack.isEmpty()) {
            return null;
        }
        return Holder.key(stack);
    }

    /** appmek未導入の環境で {@code MekanismKey} をロードしないための入れ子。 */
    private static final class Holder {

        private static AEKey key(ChemicalStack<?> stack) {
            return me.ramidzkh.mekae2.ae2.MekanismKey.of(stack);
        }
    }
}
