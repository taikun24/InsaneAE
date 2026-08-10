package jp.main.taikun.insaneae.mixin;

import appeng.util.ConfigInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@link ConfigInventory} の「1 スロットにアイテムの最大スタック数を超えて入れてよいか」
 * ({@code allowOverstacking}) を後から立てられるようにする。
 *
 * <p>これが立っていないと {@code getMaxAmount} が
 * {@code min(アイテムの最大スタック数, 容量)} を返すので、<b>容量をいくら上げても
 * アイテムは 1 スロット 64 個で頭打ち</b>になる (液体や化学物質はスタック数の概念が無いので
 * 容量がそのまま効く)。</p>
 *
 * <p>フラグはコンストラクタでしか渡せず、インベントリを作るのは
 * {@code InterfaceLogic} のコンストラクタの中なので、外から差し込む余地が無い。
 * {@code final} フィールドなので {@code @Mutable} を付けて開ける。</p>
 *
 * <p>使うのは超特大インターフェイスの構築時だけ
 * ({@link jp.main.taikun.insaneae.iface.InsaneInterfaceBlockEntity})。
 * 他のインベントリの挙動は変わらない。</p>
 */
@Mixin(value = ConfigInventory.class, remap = false)
public interface ConfigInventoryAccessor {

    @Mutable
    @Accessor("allowOverstacking")
    void insaneae$setAllowOverstacking(boolean allowOverstacking);
}
