package jp.main.taikun.insaneae.client;

import appeng.api.config.LockCraftingMode;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.client.gui.Icon;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.IconButton;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;
import com.mojang.logging.LogUtils;
import jp.main.taikun.insaneae.menu.QuantumCpuMenu;
import jp.main.taikun.insaneae.mixin.SlotAccessor;
import jp.main.taikun.insaneae.quantum.QuantumCpuBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.slf4j.Logger;

import java.util.List;

/**
 * Quantum CPU の画面。AE2 のパターンプロバイダ画面 + 加速カードのパネル + パターン枠のページ送り。
 *
 * <p>パターン枠は {@value QuantumCpuBlockEntity#PATTERN_SLOTS} 個あるが、
 * メニューに並ぶのは<b>1 ページぶん (9x6) だけ</b>。ページ送りはサーバに伝えて
 * 窓をずらしてもらう ({@link QuantumCpuMenu#setPage(int)})。全枠をスロットにすると
 * 毎 tick の同期が 1620 枠ぶん走って重いため。<b>切り替えた直後の 1 往復ぶんだけ
 * 古い中身が見えることがある</b>。</p>
 *
 * <p>プレイヤーインベントリからの Shift クリックは、メニュー側で
 * <b>ページを跨いで空き枠を埋める</b>ようにしてある ({@code QuantumCpuMenu#quickMoveStack})。</p>
 *
 * <p>レイアウトは {@code assets/ae2/screens/insaneae/quantum_cpu.json}
 * ({@code StyleManager} が ae2 名前空間固定で読むのでそちらに置いてある)。
 * 下の座標定数は JSON の {@code ENCODED_PATTERN} と揃えること。</p>
 */
public class QuantumCpuScreen extends PatternProviderScreen<QuantumCpuMenu> {

    private static final Logger LOG = LogUtils.getLogger();

    private static final int COLUMNS = QuantumCpuBlockEntity.PATTERN_COLUMNS;
    private static final int PER_PAGE = QuantumCpuBlockEntity.PATTERN_SLOTS_PER_PAGE;
    private static final int PAGES = QuantumCpuBlockEntity.PATTERN_PAGES;

    /** パターン枠の左上と間隔 (quantum_cpu.json の ENCODED_PATTERN と同じ値)。 */
    private static final int GRID_LEFT = 8;
    private static final int GRID_TOP = 22;
    private static final int SLOT_SIZE = 18;

    private final PageButton prevPage;
    private final PageButton nextPage;

    private int page;

    public QuantumCpuScreen(QuantumCpuMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);

        if (menu.getTarget() instanceof IUpgradeableObject upgradeable) {
            addUpgradesPanel(upgradeable);
        }

        // ウィジェットの追加は init() ではなくコンストラクタで行うこと
        // (WidgetContainer#add は同じ ID の二重登録で落ちるため。init は画面サイズ変更のたびに走る)
        prevPage = new PageButton(Icon.ARROW_LEFT,
                Component.translatable("gui.insaneae.quantum_cpu.previous_page"), btn -> turnPage(-1));
        nextPage = new PageButton(Icon.ARROW_RIGHT,
                Component.translatable("gui.insaneae.quantum_cpu.next_page"), btn -> turnPage(1));
        widgets.add("prevPage", prevPage);
        widgets.add("nextPage", nextPage);

        setTextContent("dialog_title", Component.translatable("block.insaneae.quantum_cpu"));
    }

    /**
     * アップグレードカードのパネル。
     *
     * <p><b>他 Mod が同じものを先に足していることがある。</b> 例えば ExtendedAE Plus は
     * {@code PatternProviderScreen} のコンストラクタに Mixin で
     * {@code widgets.add("upgrades", new UpgradesPanel(...))} を注入する。
     * こちらは {@code super(...)} の後に同じ ID で足すので、
     * {@code WidgetContainer} の重複チェック ({@code "%s already used for widget"}) に当たって
     * {@code IllegalStateException} になり、<b>画面が開けなくなる</b>
     * (相手は自分の追加だけ try/catch で守っているのでログにも出ない)。</p>
     *
     * <p>中身はどちらも同じ {@code UPGRADE} スロットなので、既にあるなら相手のパネルに任せる。</p>
     */
    private void addUpgradesPanel(IUpgradeableObject upgradeable) {
        try {
            widgets.add("upgrades", new UpgradesPanel(menu.getSlots(SlotSemantics.UPGRADE), upgradeable));
        } catch (IllegalStateException alreadyAdded) {
            LOG.debug("InsaneAE: upgrades パネルは他 Mod が追加済みなので任せる ({})",
                    alreadyAdded.getMessage());
        }
    }

    @Override
    protected void init() {
        super.init();
        // super.init() がスタイルどおりに全スロットを並べてしまうので、ここで並べ直す。
        layoutPatternSlots();
    }

    // ------------------------------------------------------------------ ページ送り

    private void turnPage(int delta) {
        setPage(page + delta);
    }

    private void setPage(int newPage) {
        int clamped = Math.max(0, Math.min(pageCount() - 1, newPage));
        if (clamped != page) {
            page = clamped;
            menu.setPage(page);
            layoutPatternSlots();
        }
    }

    private int pageCount() {
        return menu.isServerPaged() ? menu.getPageCount() : PAGES;
    }

    /**
     * 現在のページのスロットを並べる。
     *
     * <p>サーバ側でページ分割されている場合 ({@link QuantumCpuMenu#isServerPaged()})、
     * メニューには 1 ページぶんのスロットしか無いので素直に並べるだけ。
     * {@code PatternProviderMenuMixin} が効かなかった場合は全枠 (1620) 並んでいるので、
     * 表示するページぶんだけ位置を決めて残りは非表示にする<b>旧来のやり方</b>に落ちる。</p>
     */
    private void layoutPatternSlots() {
        List<Slot> slots = menu.getSlots(SlotSemantics.ENCODED_PATTERN);
        int first = menu.isServerPaged() ? 0 : page * PER_PAGE;

        for (int index = 0; index < slots.size(); index++) {
            Slot slot = slots.get(index);
            int inPage = index - first;
            boolean visible = inPage >= 0 && inPage < PER_PAGE;

            if (slot instanceof AppEngSlot appEngSlot) {
                // setSlotEnabled ではなく setActive。
                // 無効化すると mayPlace/mayPickup まで殺してしまい、Shift クリックが
                // 表示中のページにしか入らなくなる。
                appEngSlot.setActive(visible);
            }
            if (visible) {
                // Slot#x/y は final なので Mixin のアクセサ経由で書く (AE2 は AT で外している)。
                ((SlotAccessor) slot).insaneae$setX(GRID_LEFT + inPage % COLUMNS * SLOT_SIZE);
                ((SlotAccessor) slot).insaneae$setY(GRID_TOP + inPage / COLUMNS * SLOT_SIZE);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double wheelDelta) {
        if (super.mouseScrolled(x, y, wheelDelta)) {
            return true;
        }
        if (wheelDelta != 0 && isOverPatternGrid(x, y)) {
            turnPage(wheelDelta > 0 ? -1 : 1);
            return true;
        }
        return false;
    }

    private boolean isOverPatternGrid(double x, double y) {
        int left = leftPos + GRID_LEFT;
        int top = topPos + GRID_TOP;
        return x >= left && x < left + COLUMNS * SLOT_SIZE
                && y >= top && y < top + PER_PAGE / COLUMNS * SLOT_SIZE;
    }

    // -------------------------------------------------------------------- 描画

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        setTextContent("page", Component.literal((page + 1) + "/" + pageCount()));
        prevPage.active = page > 0;
        nextPage.active = page < pageCount() - 1;
        // ロック表示 (lockReason) はタイトルと同じ行に出るので、出ている間はタイトルを引っ込める。
        setTextHidden("dialog_title", menu.getLockCraftingMode() != LockCraftingMode.NONE);
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

    /** ページ送りボタン。{@link IconButton} はアイコンだけ差し替えれば使える。 */
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
}
