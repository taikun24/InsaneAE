package jp.main.taikun.insaneae.menu;

import appeng.helpers.InterfaceLogicHost;
import appeng.menu.MenuOpener;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.InterfaceMenu;
import appeng.menu.locator.MenuLocator;
import appeng.menu.locator.MenuLocators;
import jp.main.taikun.insaneae.iface.InsaneInterfaceBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.network.NetworkHooks;

/**
 * 超特大インターフェイスの画面。
 *
 * <p>中身は AE2 の {@link InterfaceMenu} そのままでよい。{@code InterfaceMenu} は
 * <b>ホストの持つ枠数だけスロットを並べる</b>作りなので、枠数を増やしただけの
 * こちらでもそのまま動く。</p>
 *
 * <p>別クラスにしているのは {@code MenuType} を分けるため。AE2 の
 * {@code MenuTypeBuilder} は {@code MenuType} を必ず {@code ae2} 名前空間で登録し、
 * 画面レイアウトも {@code ae2:screens/interface.json} 固定になるので、
 * 9x9 の枠が収まらない。{@code insaneae:insane_interface} として登録し直し、
 * レイアウトは {@code assets/ae2/screens/insaneae/insane_interface.json} を使う
 * ({@code StyleManager} が ae2 名前空間固定で読むためそちらに置いてある)。</p>
 *
 * <p>スロットは<b>全枠ぶん (設定 81 + 在庫 81) 並べてある</b>。表示するぶんを選ぶのは
 * クライアント側だけの処理 ({@code InsaneInterfaceScreen})。Quantum CPU の 1620 枠と違って
 * この程度ならバニラの差分同期で十分間に合う。</p>
 */
public class InsaneInterfaceMenu extends InterfaceMenu {

    public static final MenuType<InsaneInterfaceMenu> TYPE =
            IForgeMenuType.create(InsaneInterfaceMenu::fromNetwork);

    static {
        MenuOpener.addOpener(TYPE, InsaneInterfaceMenu::open);
    }

    private static final String ACTION_TOGGLE_PULL_MODE = "insaneae$togglePullMode";

    private final InterfaceLogicHost host;

    /** 吸い込みモードの表示用ミラー。サーバ側の {@link #broadcastChanges()} が毎 tick 同期する。 */
    @GuiSync(200)
    public boolean pullMode;

    public InsaneInterfaceMenu(int id, Inventory playerInventory, InterfaceLogicHost host) {
        super(TYPE, id, playerInventory, host);
        this.host = host;
        registerClientAction(ACTION_TOGGLE_PULL_MODE, this::togglePullMode);
    }

    /** 吸い込みモード (隣接インベントリの中身を毎 tick ME へ移す) の切り替え。 */
    public void togglePullMode() {
        if (isClientSide()) {
            sendClientAction(ACTION_TOGGLE_PULL_MODE);
        } else if (host instanceof InsaneInterfaceBlockEntity be) {
            be.setPullMode(!be.isPullMode());
        }
    }

    public boolean isPullMode() {
        return pullMode;
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide() && host instanceof InsaneInterfaceBlockEntity be) {
            pullMode = be.isPullMode();
        }
        super.broadcastChanges();
    }

    private static InsaneInterfaceMenu fromNetwork(int containerId, Inventory playerInventory,
            FriendlyByteBuf buf) {
        MenuLocator locator = MenuLocators.readFromPacket(buf);
        InterfaceLogicHost host = locator.locate(playerInventory.player, InterfaceLogicHost.class);
        if (host == null) {
            throw new IllegalStateException(
                    "Couldn't find an Insane ME Interface at " + locator + " on the client.");
        }
        InsaneInterfaceMenu menu = new InsaneInterfaceMenu(containerId, playerInventory, host);
        menu.setReturnedFromSubScreen(buf.readBoolean());
        return menu;
    }

    private static boolean open(Player player, MenuLocator locator, boolean fromSubMenu) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        InterfaceLogicHost host = locator.locate(player, InterfaceLogicHost.class);
        if (host == null) {
            return false;
        }
        Component title = host instanceof Nameable nameable && nameable.hasCustomName()
                ? nameable.getCustomName()
                : Component.empty();
        MenuProvider provider = new SimpleMenuProvider((containerId, inv, p) -> {
            InsaneInterfaceMenu menu = new InsaneInterfaceMenu(containerId, inv, host);
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
