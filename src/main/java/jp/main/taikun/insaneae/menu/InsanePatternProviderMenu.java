package jp.main.taikun.insaneae.menu;

import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
//? if >=1.21 {
import appeng.menu.locator.MenuHostLocator;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
//?}
//? if <1.21 {
/*import appeng.menu.locator.MenuLocator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.network.NetworkHooks;
*///?}

/**
 * 特大パターンプロバイダーの画面。
 *
 * <p>中身は {@link QuantumCpuMenu} そのまま (パターン枠のページ送り・全枠 Shift クリック・
 * アップグレード枠のガードは、ホストが {@code IUpgradeableObject} のときだけ働く)。
 * 別クラスにしているのは {@code MenuType} を分けるため。</p>
 */
public class InsanePatternProviderMenu extends QuantumCpuMenu {

    public static final MenuType<InsanePatternProviderMenu> TYPE =
            //? if >=1.21 {
            IMenuTypeExtension.create(InsanePatternProviderMenu::fromNetwork);
            //?} else {
            /*IForgeMenuType.create(InsanePatternProviderMenu::fromNetwork);
            *///?}

    static {
        MenuOpener.addOpener(TYPE, InsanePatternProviderMenu::open);
    }

    public InsanePatternProviderMenu(int id, Inventory playerInventory, PatternProviderLogicHost host) {
        super(TYPE, id, playerInventory, host);
    }

    private static InsanePatternProviderMenu fromNetwork(int containerId, Inventory playerInventory,
        //? if >=1.21 {
            RegistryFriendlyByteBuf buf) {
        MenuHostLocator locator = MenuLocators.readFromPacket(buf);
        //?} else {
            /*FriendlyByteBuf buf) {
        MenuLocator locator = MenuLocators.readFromPacket(buf);
        *///?}
        PatternProviderLogicHost host = locator.locate(playerInventory.player, PatternProviderLogicHost.class);
        if (host == null) {
            throw new IllegalStateException(
                    "Couldn't find an Insane Pattern Provider at " + locator + " on the client.");
        }
        InsanePatternProviderMenu menu = new InsanePatternProviderMenu(containerId, playerInventory, host);
        menu.setReturnedFromSubScreen(buf.readBoolean());
        return menu;
    }

    //? if >=1.21 {
    private static boolean open(Player player, MenuHostLocator locator, boolean fromSubMenu) {
    //?} else {
    /*private static boolean open(Player player, MenuLocator locator, boolean fromSubMenu) {
    *///?}
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        PatternProviderLogicHost host = locator.locate(player, PatternProviderLogicHost.class);
        if (host == null) {
            return false;
        }
        Component title = host instanceof Nameable nameable && nameable.hasCustomName() ?
                nameable.getCustomName()
                : Component.empty();
        MenuProvider provider = new SimpleMenuProvider((containerId, inv, p) -> {
            InsanePatternProviderMenu menu = new InsanePatternProviderMenu(containerId, inv, host);
            menu.setLocator(locator);
            return menu;
        }, title);
        //? if >=1.21 {
        serverPlayer.openMenu(provider, buf -> {
        //?} else {
        /*NetworkHooks.openScreen(serverPlayer, provider, buf -> {
        *///?}
            MenuLocators.writeToPacket(buf, locator);
            buf.writeBoolean(fromSubMenu);
        });
        return true;
    }
}
