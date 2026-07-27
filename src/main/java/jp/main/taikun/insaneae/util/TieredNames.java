package jp.main.taikun.insaneae.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 階層違いのブロック／アイテムの表示名。
 *
 * <p>階層ごとに lang のキーを並べる代わりに、<b>書式キー 1 つ + 階層ラベル</b>で組み立てる。
 * 例: {@code block.insaneae.crafting_storage = %s Crafting Storage} に {@code "1G"} を渡す。
 * 階層を増やしても lang を触らなくてよい。</p>
 *
 * <p>実装は各アイテム／ブロックの {@code getName} の上書き。
 * {@code getDescriptionId()} は階層ごとに固有のまま (lang には載せない) なので、
 * ツールチップの説明キー ({@code <翻訳キー>.desc}) は今までどおり階層ごとに書ける。</p>
 */
public final class TieredNames {

    public static final String CRAFTING_STORAGE = "block.insaneae.crafting_storage";
    public static final String CRAFTING_ACCELERATOR = "block.insaneae.crafting_accelerator";
    public static final String CELL_COMPONENT = "item.insaneae.cell_component";
    public static final String ITEM_STORAGE_CELL = "item.insaneae.item_storage_cell";
    public static final String FLUID_STORAGE_CELL = "item.insaneae.fluid_storage_cell";
    public static final String CHEMICAL_STORAGE_CELL = "item.insaneae.chemical_storage_cell";
    public static final String PORTABLE_ITEM_CELL = "item.insaneae.portable_item_cell";
    public static final String PORTABLE_FLUID_CELL = "item.insaneae.portable_fluid_cell";
    public static final String PORTABLE_CHEMICAL_CELL = "item.insaneae.portable_chemical_cell";

    private TieredNames() {
    }

    /** 書式キーに階層ラベルを流し込んだ表示名。 */
    public static MutableComponent of(String nameKey, String tierLabel) {
        return Component.translatable(nameKey, tierLabel);
    }
}
