package jp.main.taikun.insaneae.iface;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import appeng.util.ConfigInventory;
import jp.main.taikun.insaneae.menu.InsaneInterfaceMenu;
import jp.main.taikun.insaneae.mixin.ConfigInventoryAccessor;
import jp.main.taikun.insaneae.registries.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 超特大インターフェイス。AE2 の ME インターフェイスの枠数と 1 枠あたりの上限を引き上げたもの。
 *
 * <p><b>9x9 = {@value #SLOTS} 枠</b>で、<b>1 枠あたり {@value #MAX_PER_SLOT} 個</b>
 * (= 21 億 4748 万) まで持てる。全部で約 1739 億個。</p>
 *
 * <h2>なぜ 1 枠 21 億なのか</h2>
 *
 * <p>{@code Integer.MAX_VALUE} は<b>ここで足せる上限そのもの</b>であって、
 * 適当に切りのいい数字を選んだわけではない。AE2 の {@code InterfaceLogic} は
 * 「今いくつ足りないか」を {@code (int)} にキャストしてから処理する
 * ({@code updateStorage} → {@code tryUsePlan(int slot, AEKey what, int amount)})。
 * 設定値と在庫の差がここに入るので、<b>どちらも int に収まる範囲なら差も必ず int に収まり</b>、
 * 桁溢れが起きない。1 枠でもこれを超える値を入れられるようにすると、
 * 差がラップして「補充のつもりが搬出になる」ような壊れ方をする。
 * ストック数を入力する画面 ({@code SetStockAmountMenu}) も int なので、
 * ここが上限であることは AE2 側の作りとも一致している。</p>
 *
 * <p>枠を増やすぶんには制限が無いので、容量が足りなければ枠数 ({@link #ROWS}) を増やすこと。</p>
 *
 * <h2>long の値がそのまま来る場合</h2>
 *
 * <p>Mekanism 系の機械は化学物質を <b>long</b> で送ってくる (Applied Mekanistics の
 * {@code GenericStackChemicalStorage#insertChemical} がそのまま long を流す)。
 * 21 億で頭打ちにすると溢れたぶんが機械側に押し戻されてしまうので、
 * <b>未設定の枠に来たぶんは ME ネットワークへ直接入れる</b>
 * → {@link InterfaceOverflowInventory}。</p>
 */
public class InsaneInterfaceBlockEntity extends InterfaceBlockEntity {

    /** 枠の横並び数。画面のレイアウトもこの値に合わせてある。 */
    public static final int COLUMNS = 9;
    /** 枠の段数。 */
    public static final int ROWS = 9;
    /** 設定枠・在庫枠それぞれの総数。 */
    public static final int SLOTS = COLUMNS * ROWS;

    /** 1 ページに出す段数 (残りはページ送りで見る)。 */
    public static final int ROWS_PER_PAGE = 3;
    /** 1 ページに出す枠数。 */
    public static final int SLOTS_PER_PAGE = COLUMNS * ROWS_PER_PAGE;
    /** ページ数。 */
    public static final int PAGES = SLOTS / SLOTS_PER_PAGE;

    /**
     * 1 枠に入る上限。AE2 の int 処理に合わせた上限であって、任意に上げてよい値ではない
     * (クラス説明の「なぜ 1 枠 21 億なのか」を参照)。
     */
    public static final long MAX_PER_SLOT = Integer.MAX_VALUE;

    /**
     * 外に見せるインベントリ。溢れたぶんを ME ネットワークへ直接流す。
     *
     * <p>super のコンストラクタ (= {@link #createLogic()}) より後に初期化されるので、
     * ここでは {@code getInterfaceLogic()} を安全に触れる。</p>
     */
    private final InterfaceOverflowInventory exposedInventory = new InterfaceOverflowInventory(this);

    public InsaneInterfaceBlockEntity(BlockEntityType<?> blockEntityType, BlockPos pos, BlockState blockState) {
        super(blockEntityType, pos, blockState);
    }

    /**
     * 枠数と 1 枠あたりの上限を引き上げた {@link InterfaceLogic} を作る。
     *
     * <p><b>これは super のフィールド初期化子から呼ばれる。</b>
     * この時点でこのクラスのフィールドはまだ初期化されていないので、
     * 定数と super のメンバ以外に触ってはいけない。</p>
     */
    @Override
    protected InterfaceLogic createLogic() {
        return createInsaneLogic(getMainNode(), this, getItemFromBlockEntity());
    }

    /**
     * 枠数と 1 枠あたりの上限を引き上げた {@link InterfaceLogic} を作る。
     *
     * <p>ブロック版 ({@link #createLogic()}) とケーブル版 ({@link InsaneInterfacePart}) の
     * 共通部分。<b>どちらも相手のコンストラクタ途中から呼ばれる</b>ので、
     * 引数以外のインスタンス状態に触ってはいけない。</p>
     */
    static InterfaceLogic createInsaneLogic(IManagedGridNode mainNode, InterfaceLogicHost host,
            Item icon) {
        // 枠数はコンストラクタ引数で受け付けている (AE2 の既定は 9)。
        InterfaceLogic logic = new InterfaceLogic(mainNode, host, icon, SLOTS);
        widen(logic.getConfig());
        widen(logic.getStorage());
        return logic;
    }

    /**
     * 1 枠あたりの上限を引き上げる。
     *
     * <p>{@code InterfaceLogic} のコンストラクタが {@code useRegisteredCapacities()} で
     * 既定値 (アイテム 99・液体 4000mB・化学物質 4000mB) を入れているので、上書きする。
     * <b>それだけではアイテムには効かない</b>: {@code getMaxAmount} は
     * {@code min(アイテムの最大スタック数, 容量)} を返すため、{@code allowOverstacking} も
     * 立てる必要がある → {@link ConfigInventoryAccessor}。</p>
     */
    private static void widen(ConfigInventory inv) {
        ((ConfigInventoryAccessor) inv).insaneae$setAllowOverstacking(true);
        // 未登録の型は容量が Long.MAX_VALUE 扱いになり int の範囲を超えてしまうので、
        // 「今あるものを上書きする」のではなく登録済みの型を全部明示的に設定する。
        for (AEKeyType type : AEKeyTypes.getAll()) {
            inv.setCapacity(type, MAX_PER_SLOT);
        }
    }

    /** 他 Mod のアダプタに見せるインベントリ ({@code GENERIC_INTERNAL_INV})。 */
    public InterfaceOverflowInventory getExposedInventory() {
        return exposedInventory;
    }

    /**
     * 壊されたときは<b>中身をまず ME ネットワークに戻す</b>。
     *
     * <p>AE2 の既定はドロップに変換するだけだが、{@code AEItemKey#addDrops} は
     * <b>1000 スタックを超えたぶんを警告 1 行だけ出して捨てる</b>。
     * 通常のインターフェイス (1 枠 64 個 × 9 枠) なら当たらない上限だが、
     * こちらは 1 枠 21 億 × 81 枠なので、うっかり壊すと簡単に踏む。</p>
     *
     * <p>{@code AEBaseEntityBlock#onRemove} はブロックを消す<b>前</b>に呼ぶので、
     * この時点ではまだグリッドに繋がっている。ネットワークが無い・電力が無い・
     * 在庫が満杯といった理由で戻せなかったぶんは、これまでどおりドロップに回る。</p>
     */
    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        returnStorageToNetwork();
        super.addAdditionalDrops(level, pos, drops);
    }

    private void returnStorageToNetwork() {
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return;
        }
        ConfigInventory storage = getInterfaceLogic().getStorage();
        MEStorage networkInv = grid.getStorageService().getInventory();
        IActionSource source = IActionSource.ofMachine(getInterfaceLogic());
        for (int slot = 0; slot < storage.size(); slot++) {
            GenericStack stack = storage.getStack(slot);
            if (stack == null) {
                continue;
            }
            long moved = StorageHelper.poweredInsert(grid.getEnergyService(), networkInv,
                    stack.what(), stack.amount(), source);
            if (moved > 0) {
                storage.extract(slot, stack.what(), moved, Actionable.MODULATE);
            }
        }
    }

    // ---------------------------------------------------------------- 画面まわり

    // AE2 の InterfaceLogicHost の既定実装は ae2:interface の画面を開いてしまうので、
    // 自前の MenuType に差し替える (枠数もレイアウトも違うため)。
    // 優先度画面やストック数入力画面から戻ってくる先も同じく差し替える。

    @Override
    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(InsaneInterfaceMenu.TYPE, player, locator);
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.returnTo(InsaneInterfaceMenu.TYPE, player, subMenu.getLocator());
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(ModBlocks.INSANE_INTERFACE.get());
    }
}
