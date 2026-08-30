package jp.main.taikun.insaneae.integration.aco;

/**
 * ACO のクラスを<b>名前で</b>指すときの文字列をまとめる。
 *
 * <p>ACO は任意依存なので、こちら側は型に触らず {@code Class.forName} で引く。
 * 同じ完全修飾名があちこちに散らばると、ACO 側のパッケージが動いたときに
 * 直し漏れが出る (しかも「連携が黙って外れる」だけで気付けない) ので 1 か所に置く。</p>
 *
 * <p>ここにあるのは {@code String} 定数だけなので、<b>ACO が入っていない環境でも
 * 安全に読み込める</b>。定数はコンパイル時に埋め込まれるため、参照側に負担も無い。</p>
 */
public final class AcoClassNames {

    /** BigInteger 連携の公開窓口。連携が生きているかの判定もここから引く。 */
    public static final String BIG_CRAFTING_ENGINE_API =
            "com.syaru.ae2craftingoptimizer.api.big.BigCraftingEngineApi";

    private AcoClassNames() {
    }
}
