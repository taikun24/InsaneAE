package jp.main.taikun.insaneae.iface;

import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.helpers.InterfaceLogic;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import appeng.parts.PartModel;
import appeng.parts.misc.InterfacePart;
import jp.main.taikun.insaneae.InsaneAE;
import jp.main.taikun.insaneae.menu.InsaneInterfaceMenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * 超特大インターフェイスのケーブル版 (プレート)。
 *
 * <p>中身は {@link InsaneInterfaceBlockEntity} と同じ
 * <b>9x9 = {@value InsaneInterfaceBlockEntity#SLOTS} 枠 ×
 * 1 枠 {@value InsaneInterfaceBlockEntity#MAX_PER_SLOT} 個</b>。
 * 枠を広げているのは {@code InterfaceLogic} なので、AE2 の {@link InterfacePart} を継承して
 * <b>{@link #createLogic()} だけ差し替えれば</b>ブロック版とまったく同じ挙動になる
 * (グリッドノード・NBT・ドロップ・メモリーカードは AE2 側の実装がそのまま効く)。</p>
 *
 * <h2>ブロック版との違い</h2>
 * <ul>
 *   <li><b>溢れたぶんの ME 直送が無い</b> ({@link InterfaceOverflowInventory} を挟んでいない)。
 *       あれは {@code GENERIC_INTERNAL_INV} capability として<b>ブロックの面</b>に出すもので、
 *       ケーブルバスの部品には同じ口が無い。Mekanism 系の long 搬入を受けるなら
 *       ブロック版を使うこと。</li>
 *   <li>壊したときの<b>在庫の ME への返却が無い</b>。AE2 の部品はドロップに変換する
 *       ({@code addAdditionalDrops}) 実装しか持たず、そこは 1000 スタックで頭打ちになる。
 *       ケーブル版に大量在庫を貯めたまま壊さないこと。</li>
 * </ul>
 */
public class InsaneInterfacePart extends InterfacePart {

    /** 板の本体。AE2 のインターフェイス部品と同じ形で、正面だけこちらのテクスチャにしたもの。 */
    public static final ResourceLocation MODEL_BASE =
            ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "part/insane_interface_base");

    // 状態表示のインジケータ (側面の 4 本線) は AE2 のものをそのまま借りる。
    // 部品共通の絵で、ここに独自要素は無い。
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

    public InsaneInterfacePart(IPartItem<?> partItem) {
        super(partItem);
    }

    /**
     * 枠を広げた {@link InterfaceLogic} に差し替える。
     *
     * <p><b>super のコンストラクタから呼ばれる。</b>このクラスのフィールドはまだ
     * 初期化されていないので、{@code getMainNode()} と引数以外に触ってはいけない
     * ({@code getPartItem()} は {@code AEBasePart} のコンストラクタが先に入れているので使える)。</p>
     */
    @Override
    protected InterfaceLogic createLogic() {
        return InsaneInterfaceBlockEntity.createInsaneLogic(
                getMainNode(), this, getPartItem().asItem());
    }

    @Override
    public IPartModel getStaticModels() {
        if (isActive() && isPowered()) {
            return MODELS_HAS_CHANNEL;
        }
        return isPowered() ? MODELS_ON : MODELS_OFF;
    }

    // AE2 の InterfaceLogicHost の既定実装は ae2:interface の画面を開いてしまうので、
    // ブロック版と同じく自前の MenuType に差し替える (枠数もレイアウトも違うため)。

    @Override
    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(InsaneInterfaceMenu.TYPE, player, locator);
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.returnTo(InsaneInterfaceMenu.TYPE, player, subMenu.getLocator());
    }
}
