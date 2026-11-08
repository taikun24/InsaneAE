package jp.main.taikun.insaneae.iface;

import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.helpers.InterfaceLogic;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import jp.main.taikun.insaneae.menu.InsaneInterfaceMenu;
import jp.main.taikun.insaneae.registries.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
//? if >=1.21 {
import appeng.menu.locator.MenuHostLocator;
//?}
//? if <1.21 {
/*import appeng.capabilities.Capabilities;
import appeng.menu.locator.MenuLocator;
import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.Nullable;
*///?}

/**
 * 超特大インターフェイス。AE2のMEインターフェイスの枠数と1枠あたりの上限を引き上げたもの。
 *
 * <p>9x9 = {@value #SLOTS} 枠で、1枠あたり {@value #MAX_PER_SLOT} 個
 * 21億4748万まで持てる。全部で約1739億個。</p>
 *
 * <h2>なぜ1枠21億なのか</h2>
 *
 * <p>{@code Integer.MAX_VALUE} はここで足せる上限そのものであって、
 * 適当に切りのいい数字を選んだわけではない。AE2の {@code InterfaceLogic} は
 * 「今いくつ足りないか」を {@code (int)} にキャストしてから処理する
 * {@code updateStorage}から{@code tryUsePlan(int slot, AEKey what, int amount)}を呼ぶ。
 * 設定値と在庫の差がここに入るので、どちらもintに収まる範囲なら差も必ずintに収まり、
 * 桁溢れが起きない。1枠でもこれを超える値を入れられるようにすると、
 * 差がラップして「補充のつもりが搬出になる」ような壊れ方をする。
 * ストック数を入力する画面 ({@code SetStockAmountMenu}) もintなので、
 * ここが上限であることはAE2側の作りとも一致している。</p>
 *
 * <p>枠を増やすぶんには制限が無いので、容量が足りなければ枠数 ({@link #ROWS}) を増やすこと。</p>
 *
 * <h2>longの値がそのまま来る場合</h2>
 *
 * <p>Mekanism系の機械は化学物質をlongで送ってくる。Applied Mekanisticsの
 * {@code GenericStackChemicalStorage#insertChemical} がそのままlongを流す)。
 * 21億で頭打ちにすると溢れたぶんが機械側に押し戻されてしまうので、
 * 未設定の枠に来たぶんはMEネットワークへ直接入れる
 * → {@link InterfaceOverflowInventory}。</p>
 */
public class InsaneInterfaceBlockEntity extends InterfaceBlockEntity {

    /** 枠の横並び数。画面のレイアウトもこの値に合わせてある。 */
    public static final int COLUMNS = 9;
    public static final int ROWS = 9;
    /** 設定枠・在庫枠それぞれの総数。 */
    public static final int SLOTS = COLUMNS * ROWS;

    /** 1ページに出す段数。残りはページ送りで見る。 */
    public static final int ROWS_PER_PAGE = 3;
    public static final int SLOTS_PER_PAGE = COLUMNS * ROWS_PER_PAGE;
    public static final int PAGES = SLOTS / SLOTS_PER_PAGE;

    /**
     * 1枠に入る上限。AE2のint処理に合わせた上限であって、任意に上げてよい値ではない
     * クラス説明の「なぜ1枠21億なのか」を参照。
     */
    public static final long MAX_PER_SLOT = Integer.MAX_VALUE;

    /**
     * 外に見せるインベントリ。溢れたぶんをMEネットワークへ直接流す。
     *
     * <p>superのコンストラクタ (= {@link #createLogic()}) より後に初期化されるので、
     * ここでは {@code getInterfaceLogic()} を安全に触れる。</p>
     */
    private final InterfaceOverflowInventory exposedInventory = new InterfaceOverflowInventory(getMainNode(), this);

    public InsaneInterfaceBlockEntity(BlockEntityType<?> blockEntityType, BlockPos pos, BlockState blockState) {
        super(blockEntityType, pos, blockState);
    }

    /**
     * 枠数と1枠あたりの上限を引き上げた {@link InterfaceLogic} を作る → {@link InsaneInterfaces}。
     *
     * <p>これはsuperのフィールド初期化子から呼ばれる。
     * この時点でこのクラスのフィールドはまだ初期化されていないので、
     * 定数とsuperのメンバ以外に触ってはいけない。</p>
     */
    @Override
    protected InterfaceLogic createLogic() {
        return InsaneInterfaces.createLogic(getMainNode(), this, getItemFromBlockEntity());
    }

    /** 他Modのアダプタに見せるインベントリ ({@code GENERIC_INTERNAL_INV})。 */
    public InterfaceOverflowInventory getExposedInventory() {
        return exposedInventory;
    }

    /**
     * 壊されたときは中身をまずMEネットワークに戻す → {@link InsaneInterfaces#returnStorageToNetwork}。
     *
     * <p>{@code AEBaseEntityBlock#onRemove} はブロックを消す前に呼ぶので、
     * この時点ではまだグリッドに繋がっている。戻せなかったぶんはこれまでどおりドロップに回る。</p>
     */
    @Override
    //? if <1.21 {
    /*public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction facing) {
        if (capability == Capabilities.GENERIC_INTERNAL_INV) {
            return LazyOptional.of(() -> exposedInventory).cast();
        }
        return super.getCapability(capability, facing);
    }

    /^*
     * 壊されたときは中身をまずMEネットワークに戻す → {@link InsaneInterfaces#returnStorageToNetwork}。
     *
     * <p>{@code AEBaseEntityBlock#onRemove} はブロックを消す前に呼ぶので、
     * この時点ではまだグリッドに繋がっている。戻せなかったぶんはこれまでどおりドロップに回る。</p>
     ^/
    @Override
    *///?}
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        InsaneInterfaces.returnStorageToNetwork(getMainNode(), getInterfaceLogic());
        super.addAdditionalDrops(level, pos, drops);
    }

    // AE2のInterfaceLogicHostの既定実装はae2:interfaceの画面を開いてしまうので、
    // 自前のMenuTypeに差し替える。枠数もレイアウトも違うため
    // 優先度画面やストック数入力画面から戻ってくる先も同じく差し替える

    @Override
    //? if >=1.21 {
    public void openMenu(Player player, MenuHostLocator locator) {
    //?} else {
    /*public void openMenu(Player player, MenuLocator locator) {
    *///?}
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
