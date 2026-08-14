package jp.main.taikun.insaneae.integration.astral;

import appeng.api.stacks.AEKey;
import jp.main.taikun.insaneae.InsaneAE;
import mekanism.api.chemical.ChemicalStack;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * 化学物質 (ガス・注入型・顔料・スラリー) を AE2 のキーに変換する入口。
 *
 * <p>ネットワークが化学物質を持てるのは Applied Mekanistics があるときだけなので、
 * <b>appmek の型に触るのはこのクラスの中だけ</b>に閉じ込めてある
 * (未導入の環境で {@code MekanismKey} を読みに行って落ちないように、
 * 分岐してから初めてクラスをロードする)。</p>
 */
public final class AstralChemicalKey {

    private static final boolean APPMEK_LOADED = ModList.get().isLoaded(InsaneAE.APPMEK_MODID);

    private AstralChemicalKey() {
    }

    /** appmek が無い、または変換できない中身なら null (Mekanism 本来の搬出に任せる)。 */
    @Nullable
    public static AEKey of(@Nullable ChemicalStack<?> stack) {
        if (!APPMEK_LOADED || stack == null || stack.isEmpty()) {
            return null;
        }
        return Holder.key(stack);
    }

    /** appmek 未導入の環境で {@code MekanismKey} をロードしないための入れ子。 */
    private static final class Holder {

        private static AEKey key(ChemicalStack<?> stack) {
            return me.ramidzkh.mekae2.ae2.MekanismKey.of(stack);
        }
    }
}
