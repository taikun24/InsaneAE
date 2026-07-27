package jp.main.taikun.insaneae.menu;

import appeng.api.upgrades.IUpgradeableObject;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.MenuOpener;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.PatternProviderMenu;
import appeng.menu.locator.MenuLocator;
import appeng.menu.locator.MenuLocators;
import appeng.menu.slot.AppEngSlot;
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
 * <p>パターン枠は {@code PatternProviderMenu} の作りどおり<b>全 1620 個ぶんスロットを並べる</b>。
 * 表示は {@code QuantumCpuScreen} が 1 ページ (9x6) ずつに絞るだけなので、
 * ページ送りでサーバとやり取りする必要が無い。</p>
 */
public class QuantumCpuMenu extends PatternProviderMenu {

    public static final MenuType<QuantumCpuMenu> TYPE = IForgeMenuType.create(QuantumCpuMenu::fromNetwork);

    static {
        MenuOpener.addOpener(TYPE, QuantumCpuMenu::open);
    }

    public QuantumCpuMenu(int id, Inventory playerInventory, PatternProviderLogicHost host) {
        super(TYPE, id, playerInventory, host);
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
