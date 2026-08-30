package jp.main.taikun.insaneae.menu;

import appeng.api.inventories.InternalInventory;
import jp.main.taikun.insaneae.quantum.QuantumCpuBlockEntity;

/**
 * メニューを組み立てている間だけ、パターン枠を<b>1 ページぶんの窓</b>に見せるための受け渡し。
 *
 * <h2>なぜこんな形なのか</h2>
 * <p>{@code PatternProviderMenu} のコンストラクタは {@code logic.getPatternInv()} の枠数だけ
 * スロットを並べる。Quantum CPU は 1620 枠あるので、そのままだとバニラの毎 tick の同期処理が
 * 1620 枠ぶん走る ({@link PagedPatternInventory} の説明を参照)。</p>
 *
 * <p>以前はここを {@code PatternProviderMenu} への {@code @Redirect} ですり替えていたが、
 * <b>他 Mod が同じ {@code getPatternInv()} 呼び出しを {@code @Redirect} すると、
 * 優先度の高い方しか成立せずこちらが黙って外れる</b> (ExtendedAE Plus 1.6 の
 * {@code PatternProviderMenuUpgradesMixin} が優先度 2000 で実際にそうなる)。
 * 優先度を上げ返すのは相手の機能を壊すだけなので、<b>命令の取り合いから降りて</b>
 * こちらのロジック ({@link jp.main.taikun.insaneae.provider.InsanePatternProviderLogic})
 * 側の override で返す形にした。Mixin を使わないので、今後どの Mod とも競合しない。</p>
 *
 * <h2>受け渡しの流れ</h2>
 * <ol>
 *   <li>{@link QuantumCpuMenu} のコンストラクタが {@code super(...)} の引数の中で
 *       {@link #begin} を呼ぶ (super より先に評価されるのが重要)。</li>
 *   <li>super の途中で呼ばれる {@code getPatternInv()} が {@link #windowFor} を通り、窓を返す。</li>
 *   <li>super から戻った直後に {@link #finish} で窓を受け取り、メニューが持つ。</li>
 * </ol>
 *
 * <p>窓の有効範囲を「そのスレッドの super 実行中だけ」に絞っているので、
 * 通常のパターン出し入れは今までどおり全枠のインベントリを見る。</p>
 */
public final class PatternPagingHandshake {

    /** 組み立て中のメニュー 1 つぶんの状態。窓は最初に要求されたときに作る。 */
    private static final class Request {

        /** 開こうとしているプロバイダ。同一性しか見ないので型は問わない。 */
        private final Object host;

        private PagedPatternInventory window;

        private Request(Object host) {
            this.host = host;
        }
    }

    private static final ThreadLocal<Request> PENDING = new ThreadLocal<>();

    private PatternPagingHandshake() {
    }

    /**
     * これから組み立てるメニューのぶんの受け渡しを始める。ホストをそのまま返すので、
     * {@code super(type, id, playerInventory, begin(host))} と書ける。
     *
     * <p>前回の {@link #finish} が (コンストラクタの途中で例外が飛ぶなどで) 呼ばれていなくても、
     * ここで捨てるので次のメニューに持ち越さない。</p>
     */
    public static <T> T begin(T host, boolean paged) {
        PENDING.remove();
        if (paged) {
            PENDING.set(new Request(host));
        }
        return host;
    }

    /**
     * 組み立て中なら窓を、そうでなければ渡されたインベントリをそのまま返す。
     * {@code InsanePatternProviderLogic#getPatternInv()} からのみ呼ぶ。
     *
     * @param host       呼び元のロジックのホスト。組み立て中のメニューのホストと違うなら素通しする
     *                   (同じスレッドで別のプロバイダを触っている場合)
     * @param patternInv 本体 (全枠) のインベントリ
     */
    public static InternalInventory windowFor(Object host, InternalInventory patternInv) {
        Request request = PENDING.get();
        if (request == null || request.host != host) {
            return patternInv;
        }
        if (request.window == null) {
            request.window = new PagedPatternInventory(patternInv, QuantumCpuBlockEntity.PATTERN_SLOTS_PER_PAGE);
        }
        return request.window;
    }

    /**
     * 受け渡しを終えて、作られた窓を返す。
     *
     * @return 窓。ページングが無効だったり、super が一度も {@code getPatternInv()} を
     *         呼ばなかった場合は {@code null} (＝全枠がそのままスロットになっている)
     */
    public static PagedPatternInventory finish() {
        Request request = PENDING.get();
        PENDING.remove();
        return request == null ? null : request.window;
    }
}
