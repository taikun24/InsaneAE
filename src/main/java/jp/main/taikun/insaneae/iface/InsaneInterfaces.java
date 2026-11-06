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
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import appeng.util.ConfigInventory;
import jp.main.taikun.insaneae.mixin.iface.ConfigInventoryAccessor;
import net.minecraft.world.item.Item;

/**
 * 超特大インターフェイスのブロック版 ({@link InsaneInterfaceBlockEntity}) と
 * ケーブル版 ({@link InsaneInterfacePart}) で共通の処理。
 *
 * <p>どちらも AE2 のクラス (ブロックは {@code InterfaceBlockEntity}、部品は {@code InterfacePart})
 * を継承していて共通の親を挟めないので、違いの無い処理はここに置いて両方から呼ぶ。
 * 挙動を変えるときはここを直せば両方に入る。</p>
 */
public final class InsaneInterfaces {

    private InsaneInterfaces() {
    }

    /**
     * 枠数と 1 枠あたりの上限を引き上げた {@link InterfaceLogic} を作る。
     *
     * <p><b>ブロック版も部品版も相手のコンストラクタ途中から呼ぶ</b>
     * ({@code createLogic()} は super のフィールド初期化子から呼ばれる) ので、
     * 引数以外のインスタンス状態に触ってはいけない。</p>
     */
    public static InterfaceLogic createLogic(IManagedGridNode mainNode, InterfaceLogicHost host, Item icon) {
        // 枠数はコンストラクタ引数で受け付けている (AE2 の既定は 9)。
        InterfaceLogic logic = new InterfaceLogic(mainNode, host, icon, InsaneInterfaceBlockEntity.SLOTS);
        widen(logic.getConfig());
        widen(logic.getStorage());
        return logic;
    }

    /**
     * 1 枠あたりの上限を引き上げる。
     *
     * <p>{@code InterfaceLogic} のコンストラクタが既定値 (アイテム 99・液体 4000mB など) を
     * 入れているので、上書きする。
     * <b>それだけではアイテムの上限は上がらない</b>: {@code getMaxAmount} は
     * {@code min(アイテムの最大スタック数, 容量)} を返すため、{@code allowOverstacking} も
     * 立てる必要がある → {@link ConfigInventoryAccessor}。</p>
     */
    private static void widen(ConfigInventory inv) {
        ((ConfigInventoryAccessor) inv).insaneae$setAllowOverstacking(true);
        // 未登録の型は容量が Long.MAX_VALUE 扱いになり int の範囲を超えてしまうので、
        // 「今あるものを上書きする」のではなく登録済みの型を全部明示的に設定する。
        for (AEKeyType type : AEKeyTypes.getAll()) {
            inv.setCapacity(type, InsaneInterfaceBlockEntity.MAX_PER_SLOT);
        }
    }

    /**
     * 在庫をできるだけ ME ネットワークに戻す。壊されたときにドロップへ回す前に呼ぶ。
     *
     * <p>AE2 の既定はドロップに変換するだけだが、{@code AEItemKey#addDrops} は
     * <b>1000 スタックを超えたぶんを警告 1 行だけ出して捨てる</b>。
     * 通常のインターフェイス (1 枠 64 個 × 9 枠) なら当たらない上限だが、
     * こちらは 1 枠 21 億 × 81 枠なので、うっかり壊すと簡単に超えてしまう。</p>
     *
     * <p>ネットワークが無い・電力が無い・在庫が満杯といった理由で戻せなかったぶんは
     * 枠に残るので、呼び出し元がこれまでどおりドロップに回す。</p>
     */
    public static void returnStorageToNetwork(IManagedGridNode mainNode, InterfaceLogic logic) {
        IGrid grid = mainNode.getGrid();
        if (grid == null) {
            return;
        }
        ConfigInventory storage = logic.getStorage();
        MEStorage networkInv = grid.getStorageService().getInventory();
        IActionSource source = IActionSource.ofMachine(logic);
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
}
