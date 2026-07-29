package jp.main.taikun.insaneae.menu;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.MenuOpener;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.PatternProviderMenu;
import appeng.menu.locator.MenuLocator;
import appeng.menu.locator.MenuLocators;
import appeng.menu.slot.AppEngSlot;
import jp.main.taikun.insaneae.quantum.QuantumCpuBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.network.NetworkHooks;

import java.util.List;

/**
 * Quantum CPU の画面。パターンプロバイダの画面に加速カードのスロットを足しただけ。
 *
 * <p>AE2 の {@code MenuTypeBuilder} は {@code MenuType} を必ず {@code ae2} 名前空間で登録してしまうので、
 * ここでは同じ処理を自前で書いて {@code insaneae:quantum_cpu} として登録している。
 * 画面レイアウトの JSON は {@code StyleManager} が {@code ae2} 名前空間固定で読むため、
 * こちらの分も {@code assets/ae2/screens/insaneae/quantum_cpu.json} に置いてある。</p>
 *
 * <p>パターン枠は 1620 個あるが、メニューに並べるのは<b>1 ページぶん (9x6 = 54 枠) だけ</b>。
 * {@code PatternProviderMenuMixin} がコンストラクタの見るインベントリを
 * {@link PagedPatternInventory} にすり替え、ページ送りは窓をずらして中身を差し替える。
 * 全枠をスロットにするとバニラの毎 tick の同期が 1620 枠ぶん走るため
 * ({@link PagedPatternInventory} の説明を参照)。</p>
 */
public class QuantumCpuMenu extends PatternProviderMenu {

    public static final MenuType<QuantumCpuMenu> TYPE = IForgeMenuType.create(QuantumCpuMenu::fromNetwork);

    /** 表示ページの変更をサーバに伝えるアクション。 */
    private static final String ACTION_SET_PAGE = "insaneaeSetPage";

    static {
        MenuOpener.addOpener(TYPE, QuantumCpuMenu::open);
    }

    /**
     * パターン枠の窓。{@code PatternProviderMenuMixin} が <b>super のコンストラクタの中で</b>
     * {@link #insaneae$createPatternWindow} を呼んで作る。
     *
     * <p>そのため<b>この宣言に初期化子を書いてはいけない</b> (フィールド初期化子は super の後に走るので、
     * 書くと作られたばかりの窓を null で潰してしまう)。
     * Mixin が効かなかった場合はここが null のままで、パターン枠は従来どおり全枠並ぶ。</p>
     */
    private PagedPatternInventory patternWindow;

    public QuantumCpuMenu(int id, Inventory playerInventory, PatternProviderLogicHost host) {
        super(TYPE, id, playerInventory, host);

        registerClientAction(ACTION_SET_PAGE, Integer.class, this::applyPage);
        // アップグレードスロット。
        // 他 Mod が PatternProviderMenu のコンストラクタに Mixin で
        // setupUpgrades(host.getUpgrades()) を注入していることがある
        // (AppliedFlux の MixinPatternProviderMenu が実際にやっている)。
        // 相手が使うのは<b>こちらと同じインベントリ</b>なので、素直に足すと
        // 同じ中身のスロットが 2 セット並び、上下が連動する見た目になる。
        // 既に UPGRADE スロットがあるならそちらに任せる。
        if (host instanceof IUpgradeableObject upgradeable
                && getSlots(SlotSemantics.UPGRADE).isEmpty()) {
            setupUpgrades(upgradeable.getUpgrades());
        }

        // 返却インベントリには見出しを出していないので、
        // 空スロットのツールチップで何の枠か分かるようにする。
        List<Component> returnInvTooltip =
                List.of(Component.translatable("gui.insaneae.quantum_cpu.return_inventory"));
        for (Slot slot : getSlots(SlotSemantics.STORAGE)) {
            if (slot instanceof AppEngSlot appEngSlot) {
                appEngSlot.setEmptyTooltip(() -> returnInvTooltip);
            }
        }
    }

    // ------------------------------------------------------------ ページ送り

    /**
     * パターン枠の窓を作る。{@code PatternProviderMenuMixin} からのみ呼ばれる
     * (super のコンストラクタの途中なので、ここで他のフィールドを触らないこと)。
     */
    public PagedPatternInventory insaneae$createPatternWindow(InternalInventory patternInv) {
        patternWindow = new PagedPatternInventory(patternInv, QuantumCpuBlockEntity.PATTERN_SLOTS_PER_PAGE);
        return patternWindow;
    }

    /** パターン枠がサーバ側でページ分割されているか (Mixin が効いているか)。 */
    public boolean isServerPaged() {
        return patternWindow != null;
    }

    public int getPageCount() {
        return patternWindow != null
                ? patternWindow.getPageCount()
                : QuantumCpuBlockEntity.PATTERN_PAGES;
    }

    public int getPage() {
        return patternWindow != null ? patternWindow.getPage() : 0;
    }

    /**
     * 表示ページを変える。クライアントから呼ぶとサーバにも伝わり、
     * サーバは次の {@code broadcastChanges} で新しいページの中身を送ってくる
     * (＝ページ送りの直後だけ 1 往復ぶん古い中身が見えることがある)。
     */
    public void setPage(int page) {
        if (patternWindow == null) {
            return;
        }
        applyPage(page);
        if (isClientSide()) {
            sendClientAction(ACTION_SET_PAGE, page);
        }
    }

    private void applyPage(int page) {
        if (patternWindow != null) {
            patternWindow.setPage(page);
        }
    }

    /**
     * シフトクリックでパターンを入れるときは<b>表示中のページに限らず全枠</b>を使う。
     *
     * <p>メニューには 1 ページぶんのスロットしか無いので、
     * バニラ／AE2 の処理に任せると表示中のページが埋まった時点で止まってしまう。
     * パターン以外・パターン枠から取り出す側は AE2 の処理のまま。</p>
     */
    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (patternWindow == null || isClientSide()) {
            return super.quickMoveStack(player, slotIndex);
        }

        Slot clicked = slots.get(slotIndex);
        if (!isPlayerInventorySlot(clicked) || !PatternDetailsHelper.isEncodedPattern(clicked.getItem())) {
            return super.quickMoveStack(player, slotIndex);
        }

        ItemStack moving = clicked.getItem();
        ItemStack remainder = patternWindow.getBacking().addItems(moving.copy());
        if (remainder.getCount() != moving.getCount()) {
            clicked.set(remainder);
            clicked.setChanged();
            broadcastChanges();
        }
        // 何枚入ったかに関わらず、ここで打ち止め (空を返すとバニラのループが終わる)。
        return ItemStack.EMPTY;
    }

    private boolean isPlayerInventorySlot(Slot slot) {
        SlotSemantic semantic = getSlotSemantic(slot);
        return semantic == SlotSemantics.PLAYER_INVENTORY || semantic == SlotSemantics.PLAYER_HOTBAR;
    }

    private static QuantumCpuMenu fromNetwork(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        MenuLocator locator = MenuLocators.readFromPacket(buf);
        PatternProviderLogicHost host = locator.locate(playerInventory.player, PatternProviderLogicHost.class);
        if (host == null) {
            throw new IllegalStateException("Couldn't find a Quantum CPU at " + locator + " on the client.");
        }
        QuantumCpuMenu menu = new QuantumCpuMenu(containerId, playerInventory, host);
        menu.setReturnedFromSubScreen(buf.readBoolean());
        return menu;
    }

    private static boolean open(Player player, MenuLocator locator, boolean fromSubMenu) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        PatternProviderLogicHost host = locator.locate(player, PatternProviderLogicHost.class);
        if (host == null) {
            return false;
        }
        Component title = host instanceof Nameable nameable && nameable.hasCustomName()
                ? nameable.getCustomName()
                : Component.empty();
        MenuProvider provider = new SimpleMenuProvider((containerId, inv, p) -> {
            QuantumCpuMenu menu = new QuantumCpuMenu(containerId, inv, host);
            menu.setLocator(locator);
            return menu;
        }, title);
        NetworkHooks.openScreen(serverPlayer, provider, buf -> {
            MenuLocators.writeToPacket(buf, locator);
            buf.writeBoolean(fromSubMenu);
        });
        return true;
    }
}
