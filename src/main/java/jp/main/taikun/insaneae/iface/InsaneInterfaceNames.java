package jp.main.taikun.insaneae.iface;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 超特大インターフェイスの表示名。<b>1% で別名になるお遊び</b>が入っている。
 *
 * <p>抽選は<b>JVM ごとに 1 回だけ</b>。当たった起動では、ブロック・ブロックのアイテム・
 * ケーブル版の 3 つが揃って別名になる (どれか 1 つだけ変わると壊れて見えるため、
 * 名前を出す口はすべてここを通す)。</p>
 *
 * <p>クライアントとサーバで別々に引くので、専用サーバでは<b>両者の名前が食い違うことがある</b>。
 * 名前を出すのはほぼクライアント側なので表示は一貫する。</p>
 *
 * <p>翻訳キーは {@code <元のキー>.another_name}。訳が無い言語では
 * バニラの仕組みで {@code en_us} が使われる。</p>
 */
public final class InsaneInterfaceNames {

    /** 別名になる確率。 */
    private static final double ANOTHER_NAME_CHANCE = 0.01;

    /** この起動では別名か。<b>クラスの読み込み時に 1 回だけ決まる。</b> */
    private static final boolean ANOTHER_NAME = Math.random() < ANOTHER_NAME_CHANCE;

    /** ブロック版の翻訳キー。 */
    public static final String BLOCK_KEY = "block.insaneae.insane_interface";

    /** ケーブル版 (プレート) の翻訳キー。 */
    public static final String PART_KEY = "item.insaneae.insane_interface_part";

    private InsaneInterfaceNames() {
    }

    /** その翻訳キーの表示名。当たった起動でだけ {@code .another_name} を引く。 */
    public static MutableComponent of(String baseKey) {
        return Component.translatable(ANOTHER_NAME ? baseKey + ".another_name" : baseKey);
    }
}
