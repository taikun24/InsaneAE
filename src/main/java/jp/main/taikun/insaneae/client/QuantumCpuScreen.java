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
import jp.main.taikun.insaneae.mixin.ui.SlotAccessor;
import jp.main.taikun.insaneae.quantum.QuantumCpuBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.slf4j.Logger;

import java.util.List;
import java.util.function.Supplier;

/**
 * Quantum CPUの画面。AE2のパターンプロバイダ画面 + 加速カードのパネル + パターン枠のページ送り。
 *
 * <p>パターン枠は {@value QuantumCpuBlockEntity#PATTERN_SLOTS} 個あるが、
 * メニューに並ぶのは1ページぶん (9x6) だけ。ページ送りはサーバに伝えて
 * 窓をずらしてもらう ({@link QuantumCpuMenu#setPage(int)})。全枠をスロットにすると
 * 毎tickの同期が1620枠ぶん走って重いため。切り替えた直後の1往復ぶんだけ
 * 古い中身が見えることがある。</p>
 *
 * <p>プレイヤーインベントリからのShiftクリックは、メニュー側で
 * ページを跨いで空き枠を埋めるようにしてある ({@code QuantumCpuMenu#quickMoveStack})。</p>
 *
 * <p>内蔵クラフトCPUのユニット枠、つまりクラフトストレージ9x2と協調処理ユニット9x2は
 * 常設せずボタンで出す。出している間はパターン枠と入れ替わる — 常設すると画面が
 * そのぶん縦に伸びて、返却インベントリやページ送りが下へ押し出されるため。</p>
 *
 * <p>レイアウトは {@code assets/ae2/screens/insaneae/quantum_cpu.json}
 * {@code StyleManager}がae2名前空間固定で読むので、そちらに置いてある。
 * 下の座標定数はJSONの {@code ENCODED_PATTERN} と揃えること。</p>
 */
public class QuantumCpuScreen<C extends QuantumCpuMenu> extends PatternProviderScreen<C> {

    private static final Logger LOG = LogUtils.getLogger();

    private static final int COLUMNS = QuantumCpuBlockEntity.PATTERN_COLUMNS;
    private static final int PER_PAGE = QuantumCpuBlockEntity.PATTERN_SLOTS_PER_PAGE;
    private static final int PAGES = QuantumCpuBlockEntity.PATTERN_PAGES;

    /** パターン枠の左上と間隔。quantum_cpu.jsonのENCODED_PATTERNと同じ値。 */
    private static final int GRID_LEFT = 8;
    private static final int GRID_TOP = 22;
    private static final int SLOT_SIZE = 18;

    /** クラフトユニット枠の1行の数。 */
    private static final int UNIT_COLUMNS = 9;
    /** クラフトストレージ枠の1行目のy。見出しのぶん下げてある。 */
    private static final int STORAGE_UNITS_TOP = GRID_TOP + 12;
    /** 協調処理ユニット枠の1行目のy。ストレージ2行 + 見出しのぶん下。 */
    private static final int ACCELERATOR_UNITS_TOP = STORAGE_UNITS_TOP + 2 * SLOT_SIZE + 16;

    private final IconTextButton prevPage;
    private final IconTextButton nextPage;
    private final IconTextButton unitsToggle;

    private int page;

    /**
     * クラフトユニットの枠を出しているか。
     *
     * <p>ユニット枠は常設せず、ボタンでパターン枠と入れ替えて出す
     * 常設すると画面がそのぶん縦に伸びて、返却インベントリやページ送りが下に押し出される。</p>
     */
    private boolean showingUnits;

    public QuantumCpuScreen(C menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);

        if (menu.getTarget() instanceof IUpgradeableObject upgradeable) {
            addUpgradesPanel(upgradeable);
        }

        // ウィジェットの追加はinit() ではなくコンストラクタで行うこと
        // WidgetContainer#addは同じIDの二重登録で落ちるため。initは画面サイズ変更のたびに走る
        prevPage = new IconTextButton(() -> Icon.ARROW_LEFT,
                Component.translatable("gui.insaneae.quantum_cpu.previous_page"), btn -> turnPage(-1));
        nextPage = new IconTextButton(() -> Icon.ARROW_RIGHT,
                Component.translatable("gui.insaneae.quantum_cpu.next_page"), btn -> turnPage(1));
        widgets.add("prevPage", prevPage);
        widgets.add("nextPage", nextPage);

        // クラフトユニット枠の開閉。CPUを持たない特大パターンプロバイダーでは
        // ユニット枠のスロットが1つも無いので、ボタンも出さない
        //? if >=1.21 {
        unitsToggle = new IconTextButton(() -> showingUnits ? Icon.BACK : Icon.S_MACHINE,
        //?} else {
        /*unitsToggle = new IconTextButton(() -> showingUnits ? Icon.ARROW_LEFT : Icon.CRAFT_HAMMER,
        *///?}
                Component.empty(), btn -> toggleUnits());
        widgets.add("unitsToggle", unitsToggle);
        unitsToggle.visible = hasUnitSlots();
        // タイトルはスタイルJSONのdialog_titleがそのまま出る。画面ごとのtranslateキーになる
    }

    /**
     * アップグレードカードのパネル。
     *
     * <p>他Modが同じものを先に足していることがある。例えばExtendedAE Plusは
     * {@code PatternProviderScreen} のコンストラクタにMixinで
     * {@code widgets.add("upgrades", new UpgradesPanel(...))} を注入する。
     * こちらは {@code super(...)} の後に同じIDで足すので、
     * {@code WidgetContainer} の重複チェック ({@code "%s already used for widget"}) に当たって
     * {@code IllegalStateException} になり、画面が開けなくなる
     * 相手は自分の追加だけtry/catchで守っているので、ログにも出ない。</p>
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
        // super.init() がスタイルどおりに全スロットを並べてしまうので、ここで並べ直す
        layoutSlots();
    }

    // -------------------------------------------------------- クラフトユニット枠

    private boolean hasUnitSlots() {
        return !menu.getSlots(QuantumCpuMenu.CRAFTING_UNIT).isEmpty();
    }

    private void toggleUnits() {
        showingUnits = !showingUnits;
        layoutSlots();
    }

    /**
     * クラフトユニットの枠を並べる。上段がクラフトストレージ、下段が協調処理ユニットで、
     * どちらも9x2。
     *
     * <p>出していない間は {@code setActive(false)} で消す
     * {@code setSlotEnabled}ではない理由は{@link #layoutSlots()}と同じ。</p>
     */
    private void layoutUnitSlots(SlotSemantic semantic, int top, boolean visible) {
        List<Slot> slots = menu.getSlots(semantic);
        for (int index = 0; index < slots.size(); index++) {
            Slot slot = slots.get(index);
            if (slot instanceof AppEngSlot appEngSlot) {
                appEngSlot.setActive(visible);
            }
            if (visible) {
                ((SlotAccessor) slot).insaneae$setX(GRID_LEFT + index % UNIT_COLUMNS * SLOT_SIZE);
                ((SlotAccessor) slot).insaneae$setY(top + index / UNIT_COLUMNS * SLOT_SIZE);
            }
        }
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
            layoutSlots();
        }
    }

    private int pageCount() {
        return menu.isServerPaged() ? menu.getPageCount() : PAGES;
    }

    /**
     * 現在のページのパターン枠と、クラフトユニット枠を並べる。
     *
     * <p>パターン枠とユニット枠は同じ場所を取り合うので、出しているのはどちらか一方だけ。</p>
     *
     * <p>サーバ側でページ分割されている場合 ({@link QuantumCpuMenu#isServerPaged()})、
     * メニューには1ページぶんのスロットしか無いので素直に並べるだけ。
     * サーバ側のページングを切っている場合は全枠 (1620) が並んでいるので、
     * 表示するページぶんだけ位置を決めて残りは非表示にする。</p>
     */
    private void layoutSlots() {
        layoutUnitSlots(QuantumCpuMenu.CRAFTING_UNIT, STORAGE_UNITS_TOP, showingUnits);
        layoutUnitSlots(QuantumCpuMenu.ACCELERATOR_UNIT, ACCELERATOR_UNITS_TOP, showingUnits);

        List<Slot> slots = menu.getSlots(SlotSemantics.ENCODED_PATTERN);
        int first = menu.isServerPaged() ? 0 : page * PER_PAGE;

        for (int index = 0; index < slots.size(); index++) {
            Slot slot = slots.get(index);
            int inPage = index - first;
            boolean visible = !showingUnits && inPage >= 0 && inPage < PER_PAGE;

            if (slot instanceof AppEngSlot appEngSlot) {
                // setSlotEnabledではなくsetActive
                // 無効化するとmayPlace/mayPickupまで殺してしまい、Shiftクリックが
                // 表示中のページにしか入らなくなる
                appEngSlot.setActive(visible);
            }
            if (visible) {
                // Slot#x/yはfinalなのでMixinのアクセサ経由で書く。AE2はATで外している
                ((SlotAccessor) slot).insaneae$setX(GRID_LEFT + inPage % COLUMNS * SLOT_SIZE);
                ((SlotAccessor) slot).insaneae$setY(GRID_TOP + inPage / COLUMNS * SLOT_SIZE);
            }
        }
    }

    @Override
    // ページ送りは縦スクロール (wheelDelta) だけを見る。横スクロール (scrollX) は使わない
    //? if >=1.21 {
    public boolean mouseScrolled(double x, double y, double scrollX, double wheelDelta) {
        if (super.mouseScrolled(x, y, scrollX, wheelDelta)) {
    //?} else {
    /*public boolean mouseScrolled(double x, double y, double wheelDelta) {
        if (super.mouseScrolled(x, y, wheelDelta)) {
    *///?}
            return true;
        }
        if (!showingUnits && wheelDelta != 0 && isOverPatternGrid(x, y)) {
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
        // ユニット枠を出している間、パターン枠まわりの表示は引っ込める
        prevPage.visible = !showingUnits;
        nextPage.visible = !showingUnits;
        setTextHidden("page", showingUnits);
        setTextHidden("storageUnitsLabel", !showingUnits);
        setTextHidden("acceleratorUnitsLabel", !showingUnits);
        unitsToggle.setMessage(Component.translatable(showingUnits ?
                "gui.insaneae.quantum_cpu.show_patterns"
                : "gui.insaneae.quantum_cpu.show_crafting_units"));
        // ロック表示 (lockReason) はタイトルと同じ行に出るので、出ている間はタイトルを引っ込める
        setTextHidden("dialog_title", menu.getLockCraftingMode() != LockCraftingMode.NONE);
    }

    /**
     * 背景。{@code generatedBackground} は枠しか描かないので、スロットのくぼみは自分で敷く
     * 専用の背景テクスチャを持たないぶん、ページ送りで枠数が変わっても崩れない。
     */
    @Override
    public void drawBG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY,
            float partialTicks) {
        super.drawBG(guiGraphics, offsetX, offsetY, mouseX, mouseY, partialTicks);

        for (Slot slot : menu.slots) {
            // アップグレードとツールボックスは専用パネルが自前の枠を描くので敷かない
            SlotSemantic semantic = menu.getSlotSemantic(slot);
            if (slot.isActive() && semantic != SlotSemantics.UPGRADE && semantic != SlotSemantics.TOOLBOX) {
                Icon.SLOT_BACKGROUND.getBlitter()
                        .dest(offsetX + slot.x - 1, offsetY + slot.y - 1)
                        .blit(guiGraphics);
            }
        }
    }

    /**
     * アイコンだけのボタン。{@link IconButton} はアイコンを差し替えれば使える。
     *
     * <p>アイコンを供給側から取るのは、開閉ボタンが状態で見た目を変えるため
     * {@code getIcon}は毎フレーム呼ばれる。</p>
     */
    private static final class IconTextButton extends IconButton {

        private final Supplier<Icon> icon;

        private IconTextButton(Supplier<Icon> icon, Component message, Button.OnPress onPress) {
            super(onPress);
            this.icon = icon;
            setMessage(message);
        }

        @Override
        protected Icon getIcon() {
            return icon.get();
        }
    }
}
