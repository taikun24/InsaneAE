package jp.main.taikun.insaneae.iface;

import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.helpers.InterfaceLogic;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.parts.PartModel;
import appeng.parts.misc.InterfacePart;
import jp.main.taikun.insaneae.InsaneAE;
import jp.main.taikun.insaneae.menu.InsaneInterfaceMenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
//? if >=1.21 {
import appeng.menu.locator.MenuHostLocator;
//?}
//? if <1.21 {
/*import appeng.capabilities.Capabilities;
import appeng.menu.locator.MenuLocator;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
*///?}

/**
 * 超特大インターフェイスのケーブル版、つまりプレート。
 *
 * <p>中身は {@link InsaneInterfaceBlockEntity} と同じ
 * 9x9 = {@value InsaneInterfaceBlockEntity#SLOTS} 枠 ×
 * 1枠 {@value InsaneInterfaceBlockEntity#MAX_PER_SLOT} 個。
 * 枠を広げているのは {@code InterfaceLogic} なので、AE2の {@link InterfacePart} を継承して
 * {@link #createLogic()} だけ差し替えればブロック版とまったく同じ挙動になる
 * グリッドノード、NBT、ドロップ、メモリーカードはAE2側の実装がそのまま使われる。</p>
 *
 * <h2>ブロック版と共通の処理</h2>
 * <ul>
 *   <li>外の機械に見せるインベントリ ({@code GENERIC_INTERNAL_INV}) は、ブロック版と同じく
 *       未設定の枠に来たぶんをMEネットワークへ直送する
 *       {@link InterfaceOverflowInventory}。登録は {@code ModCapabilities#registerParts}。
 *       AE2は部品のcapabilityをクラスの完全一致で引くので、
 *       AE2の {@code InterfacePart} 向けの登録はこの部品には適用されない。</li>
 *   <li>取り外したときは在庫をまずMEネットワークに戻す ({@link #addAdditionalDrops})。</li>
 * </ul>
 * <p>中身の処理は {@link InsaneInterfaces} にまとめてあり、ブロック版と同じものを呼んでいる。</p>
 */
public class InsaneInterfacePart extends InterfacePart {

    /** 板の本体。AE2のインターフェイス部品と同じ形で、正面だけこちらのテクスチャにしたもの。 */
    public static final ResourceLocation MODEL_BASE =
            ResourceLocation.fromNamespaceAndPath(InsaneAE.MODID, "part/insane_interface_base");

    // 状態表示のインジケータ、つまり側面の4本線はAE2のものをそのまま借りる
    // 部品共通の絵で、ここに独自要素は無い
    private static final ResourceLocation INDICATOR_OFF = ae2("part/interface_off");
    private static final ResourceLocation INDICATOR_ON = ae2("part/interface_on");
    private static final ResourceLocation INDICATOR_HAS_CHANNEL = ae2("part/interface_has_channel");

    public static final PartModel MODELS_OFF = new PartModel(MODEL_BASE, INDICATOR_OFF);
    public static final PartModel MODELS_ON = new PartModel(MODEL_BASE, INDICATOR_ON);
    public static final PartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, INDICATOR_HAS_CHANNEL);

    /** モデルの焼き込み対象としてAE2に知らせるもの一式 → {@code ModParts}。 */
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
     * <p>superのコンストラクタから呼ばれる。このクラスのフィールドはまだ
     * 初期化されていないので、{@code getMainNode()} と引数以外に触ってはいけない
     * ({@code getPartItem()} は {@code AEBasePart} のコンストラクタが先に入れているので使える)。</p>
     */
    @Override
    protected InterfaceLogic createLogic() {
        return InsaneInterfaces.createLogic(getMainNode(), this, getPartItem().asItem());
    }

    /**
     * 外の機械に見せるインベントリ。
     *
     * <p>superのコンストラクタ (= {@link #createLogic()}) より後に初期化されるので、
     * ここでは {@code getInterfaceLogic()} を安全に触れる。</p>
     */
    private final InterfaceOverflowInventory exposedInventory =
            new InterfaceOverflowInventory(getMainNode(), this);

    /** 他Modのアダプタに見せるインベントリ ({@code GENERIC_INTERNAL_INV})。 */
    public InterfaceOverflowInventory getExposedInventory() {
        return exposedInventory;
    }

    /**
     * 取り外されたときは中身をまずMEネットワークに戻す
     * → {@link InsaneInterfaces#returnStorageToNetwork}。
     *
     * <p>この時点でグリッドから切り離されていれば何もせず、これまでどおりドロップに回る。</p>
     */
    @Override
    //? if <1.21 {
    /*public <T> LazyOptional<T> getCapability(Capability<T> capabilityClass) {
        if (capabilityClass == Capabilities.GENERIC_INTERNAL_INV) {
            return LazyOptional.of(() -> exposedInventory).cast();
        }
        return super.getCapability(capabilityClass);
    }

    /^*
     * 取り外されたときは中身をまずMEネットワークに戻す
     * → {@link InsaneInterfaces#returnStorageToNetwork}。
     *
     * <p>この時点でグリッドから切り離されていれば何もせず、これまでどおりドロップに回る。</p>
     ^/
    @Override
    *///?}
    public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        InsaneInterfaces.returnStorageToNetwork(getMainNode(), getInterfaceLogic());
        super.addAdditionalDrops(drops, wrenched);
    }

    @Override
    public IPartModel getStaticModels() {
        if (isActive() && isPowered()) {
            return MODELS_HAS_CHANNEL;
        }
        return isPowered() ? MODELS_ON : MODELS_OFF;
    }

    // AE2のInterfaceLogicHostの既定実装はae2:interfaceの画面を開いてしまうので、
    // ブロック版と同じく自前のMenuTypeに差し替える。枠数もレイアウトも違うため

    @Override
    //? if >=1.21 {
    public void openMenu(Player player, MenuHostLocator locator) {
    //?} else {
    /*public void openMenu(Player player, MenuLocator locator) {
    *///?}
        MenuOpener.open(InsaneInterfaceMenu.TYPE, player, locator);
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.returnTo(InsaneInterfaceMenu.TYPE, player, subMenu.getLocator());
    }
}
