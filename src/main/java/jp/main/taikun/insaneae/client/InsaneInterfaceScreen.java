package jp.main.taikun.insaneae.client;

import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.client.gui.Icon;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.IconButton;
import appeng.client.gui.widgets.ServerSettingToggleButton;
import appeng.client.gui.widgets.SettingToggleButton;
import appeng.core.definitions.AEItems;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.FakeSlot;
import jp.main.taikun.insaneae.iface.InsaneInterfaceBlockEntity;
import jp.main.taikun.insaneae.menu.InsaneInterfaceMenu;
import jp.main.taikun.insaneae.mixin.SlotAccessor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.List;

/**
 * 超特大インターフェイスの画面。設定枠 81 + 在庫枠 81 を 9x3 ずつ 3 ページに分けて出す。
 *
 * <p>メニューには<b>全枠ぶんのスロットが並んでいる</b>ので、ページ送りは
 * 「表示するスロットの位置を決めて、残りを非表示にする」だけの<b>クライアント処理</b>
 * (サーバとのやり取りは無い)。Quantum CPU のパターン枠と違って 162 枠なら
 * バニラの差分同期で十分間に合うため、サーバ側でページ分割する必要が無い。</p>
 *
 * <h2>ストック数の設定</h2>
 *
 * <p>AE2 の ME インターフェイスは<b>設定枠 1 つにつき歯車ボタン 1 つ</b>を枠の真上に並べるが、
 * 9x9 では並べる場所が無い (ボタンの行を挟むと画面が 1.5 倍の高さになる)。
 * 代わりに<b>歯車ボタン 1 つをモード切り替え</b>にしてあり、押してから設定枠をクリックすると
 * AE2 と同じストック数入力画面が開く。</p>
 *
 * <p>レイアウトは {@code assets/ae2/screens/insaneae/insane_interface.json}
 * ({@code StyleManager} が ae2 名前空間固定で読むのでそちらに置いてある)。
 * 下の座標定数は JSON の CONFIG / STORAGE と揃えること。</p>
 */
public class InsaneInterfaceScreen extends UpgradeableScreen<InsaneInterfaceMenu> {

    private static final int COLUMNS = InsaneInterfaceBlockEntity.COLUMNS;
    private static final int PER_PAGE = InsaneInterfaceBlockEntity.SLOTS_PER_PAGE;
    private static final int PAGES = InsaneInterfaceBlockEntity.PAGES;
    private static final int ROWS_PER_PAGE = InsaneInterfaceBlockEntity.ROWS_PER_PAGE;

    /** スロットの左上と間隔 (insane_interface.json の CONFIG / STORAGE と同じ値)。 */
    private static final int GRID_LEFT = 8;
    private static final int CONFIG_TOP = 32;
    private static final int STORAGE_TOP = 102;
    private static final int SLOT_SIZE = 18;

    private final SettingToggleButton<FuzzyMode> fuzzyMode;
    private final PageButton prevPage;
    private final PageButton nextPage;
    private final AmountModeButton amountMode;
    private final PullModeButton pullMode;

    private int page;

    public InsaneInterfaceScreen(InsaneInterfaceMenu menu, Inventory playerInventory, Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);

        // ウィジェットの追加は init() ではなくコンストラクタで行うこと
        // (WidgetContainer#add は同じ ID の二重登録で落ちる。init は画面サイズ変更のたびに走る)。
        fuzzyMode = addToLeftToolbar(new ServerSettingToggleButton<>(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL));
        widgets.addOpenPriorityButton();

        prevPage = new PageButton(Icon.ARROW_LEFT,
                Component.translatable("gui.insaneae.insane_interface.previous_page"), btn -> turnPage(-1));
        nextPage = new PageButton(Icon.ARROW_RIGHT,
                Component.translatable("gui.insaneae.insane_interface.next_page"), btn -> turnPage(1));
        amountMode = new AmountModeButton();
        pullMode = new PullModeButton(btn -> menu.togglePullMode());
        widgets.add("prevPage", prevPage);
        widgets.add("nextPage", nextPage);
        widgets.add("setAmountMode", amountMode);
        widgets.add("togglePullMode", pullMode);
    }

    @Override
    protected void init() {
        super.init();
        // super.init() がスタイルどおりに全スロットを並べてしまうので、ここで並べ直す。
        layoutSlots();
    }

    // ------------------------------------------------------------------ ページ送り

    private void turnPage(int delta) {
        int clamped = Math.max(0, Math.min(PAGES - 1, page + delta));
        if (clamped != page) {
            page = clamped;
            layoutSlots();
        }
    }

    /** 現在のページのスロットだけを並べ、残りは非表示にする。 */
    private void layoutSlots() {
        layoutGrid(menu.getSlots(SlotSemantics.CONFIG), CONFIG_TOP);
        layoutGrid(menu.getSlots(SlotSemantics.STORAGE), STORAGE_TOP);
    }

    private void layoutGrid(List<Slot> slots, int top) {
        int first = page * PER_PAGE;
        for (int index = 0; index < slots.size(); index++) {
            Slot slot = slots.get(index);
            int inPage = index - first;
            boolean visible = inPage >= 0 && inPage < PER_PAGE;

            if (slot instanceof AppEngSlot appEngSlot) {
                // setSlotEnabled ではなく setActive。
                // 無効化すると mayPlace/mayPickup まで殺してしまう。
                appEngSlot.setActive(visible);
            }
            if (visible) {
                // Slot#x/y は final なので Mixin のアクセサ経由で書く (AE2 は AT で外している)。
                ((SlotAccessor) slot).insaneae$setX(GRID_LEFT + inPage % COLUMNS * SLOT_SIZE);
                ((SlotAccessor) slot).insaneae$setY(top + inPage / COLUMNS * SLOT_SIZE);
            }
        }
    }

    @Override
    // 1.21 で横スクロール (scrollX) が引数に加わった。ページ送りは縦だけ見る。
    public boolean mouseScrolled(double x, double y, double scrollX, double wheelDelta) {
        if (super.mouseScrolled(x, y, scrollX, wheelDelta)) {
            return true;
        }
        if (wheelDelta != 0 && isOverGrid(x, y)) {
            turnPage(wheelDelta > 0 ? -1 : 1);
            return true;
        }
        return false;
    }

    private boolean isOverGrid(double x, double y) {
        int left = leftPos + GRID_LEFT;
        int height = ROWS_PER_PAGE * SLOT_SIZE;
        if (x < left || x >= left + COLUMNS * SLOT_SIZE) {
            return false;
        }
        int configTop = topPos + CONFIG_TOP;
        int storageTop = topPos + STORAGE_TOP;
        return (y >= configTop && y < configTop + height) || (y >= storageTop && y < storageTop + height);
    }

    // -------------------------------------------------------------- ストック数の設定

    /**
     * ストック数モードのときは、設定枠のクリックを<b>フィルタの編集ではなく</b>
     * ストック数入力画面 ({@code SetStockAmountMenu}) に回す。
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (amountMode.isSelected() && button == 0
                && hoveredSlot instanceof FakeSlot fakeSlot
                && menu.getSlotSemantic(fakeSlot) == SlotSemantics.CONFIG
                && !fakeSlot.getItem().isEmpty()) {
            menu.openSetAmountMenu(fakeSlot.getContainerSlot());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // -------------------------------------------------------------------- 描画

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        fuzzyMode.set(menu.getFuzzyMode());
        fuzzyMode.setVisibility(menu.hasUpgrade(AEItems.FUZZY_CARD));
        setTextContent("page", Component.literal((page + 1) + "/" + PAGES));
        prevPage.active = page > 0;
        nextPage.active = page < PAGES - 1;
        pullMode.setState(menu.isPullMode());
    }

    /**
     * 背景。{@code generatedBackground} は枠しか描かないので、スロットのくぼみは自分で敷く
     * (専用の背景テクスチャを持たないぶん、ページ送りで枠数が変わっても崩れない)。
     */
    @Override
    public void drawBG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY,
            float partialTicks) {
        super.drawBG(guiGraphics, offsetX, offsetY, mouseX, mouseY, partialTicks);

        for (Slot slot : menu.slots) {
            // アップグレードとツールボックスは専用パネルが自前の枠を描くので敷かない。
            SlotSemantic semantic = menu.getSlotSemantic(slot);
            if (slot.isActive() && semantic != SlotSemantics.UPGRADE && semantic != SlotSemantics.TOOLBOX) {
                Icon.SLOT_BACKGROUND.getBlitter()
                        .dest(offsetX + slot.x - 1, offsetY + slot.y - 1)
                        .blit(guiGraphics);
            }
        }
    }

    /** ページ送りボタン。 */
    private static final class PageButton extends IconButton {

        private final Icon icon;

        private PageButton(Icon icon, Component message, Button.OnPress onPress) {
            super(onPress);
            this.icon = icon;
            setMessage(message);
        }

        @Override
        protected Icon getIcon() {
            return icon;
        }
    }

    /**
     * 吸い込みモードの切り替えボタン。状態はサーバ持ち ({@code InsaneInterfaceBlockEntity}) で、
     * ここは表示と切り替え要求だけ。
     */
    private static final class PullModeButton extends IconButton {

        private boolean state;

        private PullModeButton(Button.OnPress onPress) {
            super(onPress);
        }

        private void setState(boolean state) {
            this.state = state;
            setMessage(Component.translatable(state
                    ? "gui.insaneae.insane_interface.pull_mode_on"
                    : "gui.insaneae.insane_interface.pull_mode_off"));
        }

        @Override
        protected Icon getIcon() {
            return state ? Icon.AUTO_EXPORT_ON : Icon.AUTO_EXPORT_OFF;
        }
    }

    /** ストック数モードの切り替えボタン。押されている間は歯車が点灯する。 */
    private static final class AmountModeButton extends IconButton {

        private boolean selected;

        private AmountModeButton() {
            // 押した本人が引数で来るので、自分のフィールドを触るのに外の参照が要らない。
            super(btn -> ((AmountModeButton) btn).selected = !((AmountModeButton) btn).selected);
            setMessage(Component.translatable("gui.insaneae.insane_interface.set_amount_mode"));
        }

        private boolean isSelected() {
            return selected;
        }

        @Override
        protected Icon getIcon() {
            return selected || isHoveredOrFocused() ? Icon.COG : Icon.COG_DISABLED;
        }
    }
}
