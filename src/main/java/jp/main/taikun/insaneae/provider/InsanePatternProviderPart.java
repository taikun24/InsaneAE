package jp.main.taikun.insaneae.provider;

import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocator;
import appeng.parts.PartModel;
import appeng.parts.crafting.PatternProviderPart;
import jp.main.taikun.insaneae.InsaneAE;
import jp.main.taikun.insaneae.menu.InsanePatternProviderMenu;
import jp.main.taikun.insaneae.quantum.QuantumCpuBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * 特大パターンプロバイダーのケーブル版 (プレート)。
 *
 * <p>枠数は {@link InsanePatternProviderBlockEntity} と同じ
 * {@value QuantumCpuBlockEntity#PATTERN_SLOTS} 枠。枠を広げているのも
 * まとめ更新を入れているのも {@link InsanePatternProviderLogic} なので、
 * AE2 の {@link PatternProviderPart} を継承して <b>{@link #createLogic()} だけ差し替える</b>。</p>
 *
 * <h2>毎 tick の flush が要らない理由</h2>
 * <p>ブロック版は {@code serverTick()} で {@code flushPatternUpdate()} を流しているが、
 * ケーブル版に相当する tick は無い。{@link InsanePatternProviderLogic} は
 * <b>その tick の最初の更新をその場で流し</b>、パターンを実際に読む経路
 * ({@code getAvailablePatterns()} / {@code hasPattern}) も必ず先に流すので、
 * tick が無くても古い一覧が見えることはない
 * (溜まるのは「同じ tick に 2 回以上いじったとき」だけで、次の読み出しで解消する)。</p>
 *
 * <h2>ブロック版との違い</h2>
 * <p>返却インベントリを外の機械へ見せる {@code GENERIC_INTERNAL_INV} は
 * <b>ブロックの面</b>に出しているもので、ケーブルバスの部品には同じ口が無い。
 * 押し出し先は AE2 の部品と同じく<b>取り付けた面の隣</b>だけになる
 * (ブロック版は 6 方向すべて)。</p>
 */
public class InsanePatternProviderPart extends PatternProviderPart {

    /** 板の本体。AE2 のパターンプロバイダ部品と同じ形で、正面だけこちらのテクスチャにしたもの。 */
    public static final ResourceLocation MODEL_BASE = ResourceLocation.fromNamespaceAndPath(
            InsaneAE.MODID, "part/insane_pattern_provider_base");

    // 状態表示のインジケータ (側面の 4 本線) は AE2 のものをそのまま借りる。
    private static final ResourceLocation INDICATOR_OFF = ae2("part/interface_off");
    private static final ResourceLocation INDICATOR_ON = ae2("part/interface_on");
    private static final ResourceLocation INDICATOR_HAS_CHANNEL = ae2("part/interface_has_channel");

    public static final PartModel MODELS_OFF = new PartModel(MODEL_BASE, INDICATOR_OFF);
    public static final PartModel MODELS_ON = new PartModel(MODEL_BASE, INDICATOR_ON);
    public static final PartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, INDICATOR_HAS_CHANNEL);

    /** モデルの焼き込み対象として AE2 に知らせるもの一式 → {@code ModParts}。 */
    public static List<ResourceLocation> models() {
        return List.of(MODEL_BASE, INDICATOR_OFF, INDICATOR_ON, INDICATOR_HAS_CHANNEL);
    }

    private static ResourceLocation ae2(String path) {
        return ResourceLocation.fromNamespaceAndPath("ae2", path);
    }

    public InsanePatternProviderPart(IPartItem<?> partItem) {
        super(partItem);
    }

    /**
     * 枠を広げた {@link InsanePatternProviderLogic} に差し替える。
     *
     * <p><b>super のコンストラクタから呼ばれる。</b>このクラスのフィールドはまだ
     * 初期化されていないので、{@code getMainNode()} 以外に触ってはいけない。</p>
     */
    @Override
    protected PatternProviderLogic createLogic() {
        return new InsanePatternProviderLogic(getMainNode(), this, QuantumCpuBlockEntity.PATTERN_SLOTS);
    }

    @Override
    public IPartModel getStaticModels() {
        if (isActive() && isPowered()) {
            return MODELS_HAS_CHANNEL;
        }
        return isPowered() ? MODELS_ON : MODELS_OFF;
    }

    // AE2 の PatternProviderLogicHost の既定実装は ae2:pattern_provider の画面を開いてしまう。
    // 1620 枠はそのレイアウトに収まらないので、ブロック版と同じ画面に差し替える。

    @Override
    public void openMenu(Player player, MenuLocator locator) {
        MenuOpener.open(InsanePatternProviderMenu.TYPE, player, locator);
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.returnTo(InsanePatternProviderMenu.TYPE, player, subMenu.getLocator());
    }
}
