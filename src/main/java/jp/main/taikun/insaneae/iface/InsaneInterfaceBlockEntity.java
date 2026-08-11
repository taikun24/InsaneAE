package jp.main.taikun.insaneae.iface;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.blockentity.ServerTickingBlockEntity;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.capabilities.Capabilities;
import appeng.helpers.InterfaceLogic;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocator;
import appeng.util.ConfigInventory;
import jp.main.taikun.insaneae.menu.InsaneInterfaceMenu;
import jp.main.taikun.insaneae.mixin.ConfigInventoryAccessor;
import jp.main.taikun.insaneae.registries.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

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
public class InsaneInterfaceBlockEntity extends InterfaceBlockEntity implements ServerTickingBlockEntity {

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
     * 吸い込みモードが 1 tick に呼ぶ取り出し回数の上限。
     *
     * <p>アイテムの受け渡しは {@link IItemHandler#extractItem} が 1 回に 1 スタックまでしか
     * 返さないので、大量に動かすには呼び出しを繰り返すしかない。無制限に繰り返すと
     * 巨大な倉庫が隣にあるとき 1 tick が終わらなくなるため、回数で頭を打つ
     * (1024 回 × 1 スタックが 1 tick の上限。残りは次の tick に続きから吸う)。</p>
     */
    private static final int PULL_CALL_BUDGET = 1024;

    /**
     * 外に見せるインベントリ。溢れたぶんを ME ネットワークへ直接流す。
     *
     * <p>super のコンストラクタ (= {@link #createLogic()}) より後に初期化されるので、
     * ここでは {@code getInterfaceLogic()} を安全に触れる。</p>
     */
    private final InterfaceOverflowInventory exposedInventory = new InterfaceOverflowInventory(this);

    /** 吸い込みモード。有効な間、隣接インベントリの中身を毎 tick ME ネットワークへ移す。 */
    private boolean pullMode;

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
        // 枠数はコンストラクタ引数で受け付けている (AE2 の既定は 9)。
        InterfaceLogic logic = new InterfaceLogic(getMainNode(), this, getItemFromBlockEntity(), SLOTS);
        widen(logic.getConfig());
        widen(logic.getStorage());
        return logic;
    }

    /**
     * 1 枠あたりの上限を引き上げる。
     *
     * <p>{@code InterfaceLogic} のコンストラクタが既定値 (アイテム 99・液体 4000mB など) を
     * 入れているので、上書きする。<b>それだけではアイテムには効かない</b>:
     * {@code getMaxAmount} は {@code min(アイテムの最大スタック数, 容量)} を返すため、
     * {@code allowOverstacking} も立てる必要がある → {@link ConfigInventoryAccessor}。</p>
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

    // ---------------------------------------------------------------- 吸い込みモード

    public boolean isPullMode() {
        return pullMode;
    }

    public void setPullMode(boolean pullMode) {
        if (this.pullMode != pullMode) {
            this.pullMode = pullMode;
            saveChanges();
        }
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        data.putBoolean("pullMode", pullMode);
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        pullMode = data.getBoolean("pullMode");
    }

    /**
     * 吸い込みモード: 隣接インベントリの中身を ME ネットワークへ直接移す。
     *
     * <p>押し込み ({@code IItemHandler#insertItem}) は<b>送り手が 1 tick に 1 スタックずつ</b>
     * しか渡してこないため、スタックサイズがそのまま搬入速度の上限になってしまう
     * (BiggerStacks 等でスタックが 65536 でも 65536 個/t 止まり)。受け手側から
     * <b>1 tick に何度も取り出せば</b>この上限を超えられる。上限は
     * {@link #PULL_CALL_BUDGET} 回 × スタックサイズ / tick。</p>
     *
     * <p>電力はネットワーク在庫への搬入と同じ規約 ({@code poweredInsert}) で消費する。
     * 電力が足りないぶんは移動しない (取りこぼしは起きない: 先に受け入れ可能量を
     * シミュレートしてから、その量だけ取り出して入れる)。</p>
     */
    @Override
    public void serverTick() {
        if (!pullMode || !getMainNode().isActive()) {
            return;
        }
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return;
        }
        MEStorage networkInv = grid.getStorageService().getInventory();
        IActionSource source = IActionSource.ofMachine(getInterfaceLogic());

        int calls = PULL_CALL_BUDGET;
        for (Direction side : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(side));
            if (neighbor == null) {
                continue;
            }
            IItemHandler handler = neighbor
                    .getCapability(ForgeCapabilities.ITEM_HANDLER, side.getOpposite())
                    .orElse(null);
            if (handler == null) {
                continue;
            }
            calls = pullFrom(handler, grid, networkInv, source, calls);
            if (calls <= 0) {
                return;
            }
        }
    }

    /** 1 つのインベントリから吸えるだけ吸う。残りの呼び出し回数を返す。 */
    private int pullFrom(IItemHandler handler, IGrid grid, MEStorage networkInv, IActionSource source,
            int calls) {
        for (int slot = 0; slot < handler.getSlots() && calls > 0; slot++) {
            while (calls > 0) {
                calls--;
                ItemStack available = handler.extractItem(slot, Integer.MAX_VALUE, true);
                if (available.isEmpty()) {
                    break;
                }
                AEItemKey what = AEItemKey.of(available);
                long accepted = StorageHelper.poweredInsert(grid.getEnergyService(), networkInv,
                        what, available.getCount(), source, Actionable.SIMULATE);
                if (accepted <= 0) {
                    // ネットワークが満杯か電力切れ。このアイテムはこれ以上入らない。
                    break;
                }
                ItemStack extracted = handler.extractItem(slot, (int) accepted, false);
                if (extracted.isEmpty()) {
                    break;
                }
                long inserted = StorageHelper.poweredInsert(grid.getEnergyService(), networkInv,
                        AEItemKey.of(extracted), extracted.getCount(), source, Actionable.MODULATE);
                if (inserted < extracted.getCount()) {
                    // シミュレートと実行の間で状況が変わった場合の保険。取り出してしまった
                    // ぶんは在庫枠に置き、次の tick に InterfaceLogic が押し出す。
                    stashLeftover(AEItemKey.of(extracted), extracted.getCount() - inserted);
                    return 0;
                }
                // extractItem はスタック単位で頭打ちになる相手が多いので、
                // 同じ枠が空になるまで続けて吸う (ループ先頭のシミュレートで空を検知して抜ける)。
            }
        }
        return calls;
    }

    /** ネットワークに入り切らなかったぶんを在庫枠へ退避する (消滅させないための保険)。 */
    private void stashLeftover(AEKey what, long amount) {
        ConfigInventory storage = getInterfaceLogic().getStorage();
        for (int slot = 0; slot < storage.size() && amount > 0; slot++) {
            amount -= storage.insert(slot, what, amount, Actionable.MODULATE);
        }
    }

    /**
     * {@code GENERIC_INTERNAL_INV} を素の在庫ではなく {@link InterfaceOverflowInventory}
     * に差し替える。それ以外 (ME_STORAGE やアイテム/液体アダプタ) は AE2 のまま。
     *
     * <p>AE2 は全 BlockEntity に「{@code GENERIC_INTERNAL_INV} があれば IItemHandler /
     * IFluidHandler のアダプタを足す」capability を付けている ({@code InitCapabilities}
     * の {@code registerGenericInvWrapper})。Applied Mekanistics も同じ仕組みで
     * IChemicalHandler を足すので、ここを差し替えるだけで全アダプタが溢れ対応になる。</p>
     */
    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction facing) {
        if (capability == Capabilities.GENERIC_INTERNAL_INV) {
            return LazyOptional.of(() -> exposedInventory).cast();
        }
        return super.getCapability(capability, facing);
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
    public void openMenu(Player player, MenuLocator locator) {
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
