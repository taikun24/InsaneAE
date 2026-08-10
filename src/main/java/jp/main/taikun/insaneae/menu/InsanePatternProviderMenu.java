package jp.main.taikun.insaneae.menu;

import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import appeng.menu.locator.MenuLocators;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

/**
 * 特大パターンプロバイダーの画面。
 *
 * <p>中身は {@link QuantumCpuMenu} そのまま (パターン枠のページ送り・全枠 Shift クリック・
 * アップグレード枠のガードはホストが {@code IUpgradeableObject} のときだけ効く)。
 * 別クラスにしているのは {@code MenuType} を分けるためで、
 * {@code PatternProviderMenuMixin} の instanceof 判定はサブクラスなのでそのまま通る。</p>
 */
public class InsanePatternProviderMenu extends QuantumCpuMenu {

    public static final MenuType<InsanePatternProviderMenu> TYPE =
            IMenuTypeExtension.create(InsanePatternProviderMenu::fromNetwork);

    static {
        MenuOpener.addOpener(TYPE, InsanePatternProviderMenu::open);
    }

    public InsanePatternProviderMenu(int id, Inventory playerInventory, PatternProviderLogicHost host) {
        super(TYPE, id, playerInventory, host);
    }

    private static InsanePatternProviderMenu fromNetwork(int containerId, Inventory playerInventory,
            RegistryFriendlyByteBuf buf) {
        MenuHostLocator locator = MenuLocators.readFromPacket(buf);
        PatternProviderLogicHost host = locator.locate(playerInventory.player, PatternProviderLogicHost.class);
        if (host == null) {
            throw new IllegalStateException(
                    "Couldn't find an Insane Pattern Provider at " + locator + " on the client.");
        }
        InsanePatternProviderMenu menu = new InsanePatternProviderMenu(containerId, playerInventory, host);
        menu.setReturnedFromSubScreen(buf.readBoolean());
        return menu;
    }

    private static boolean open(Player player, MenuHostLocator locator, boolean fromSubMenu) {
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
            InsanePatternProviderMenu menu = new InsanePatternProviderMenu(containerId, inv, host);
            menu.setLocator(locator);
            return menu;
        }, title);
        serverPlayer.openMenu(provider, buf -> {
            MenuLocators.writeToPacket(buf, locator);
            buf.writeBoolean(fromSubMenu);
        });
        return true;
    }
}
