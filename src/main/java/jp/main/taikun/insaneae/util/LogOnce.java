package jp.main.taikun.insaneae.util;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 同じ内容のログを 1 回だけ出すための記録。
 *
 * <p>ブロックエンティティの生成やクラフト 1 回ごとに通る処理でログを出すと、
 * 大きなワールドではそれだけでコンソールが埋まり、保存や終了まで遅くなる (issue #18)。
 * そういう場所では {@code if (LogOnce.first("...")) LOGGER.info(...)} のように、
 * 初回だけ INFO / WARN にして 2 回目以降は出さない (または DEBUG に落とす)。</p>
 *
 * <p>鍵には数値などの可変部分を含めてもよいが、記録は {@value #MAX_KEYS} 件で打ち切る。
 * それを超えた鍵は「出し済み」として扱うので、ログが止まることはあっても増え続けはしない。</p>
 *
 * <p>Mixin からも呼ぶので、Minecraft / AE2 のクラスには依存させないこと。</p>
 */
public final class LogOnce {

    /** 覚えておく鍵の上限。理由文に座標や量を含めても際限なく増えないように。 */
    static final int MAX_KEYS = 1024;

    private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

    private LogOnce() {
    }

    /**
     * その鍵で初めて呼ばれたときだけ {@code true} を返す。
     * 記録が上限に達したあとは、新しい鍵にも {@code false} を返す。
     */
    public static boolean first(String key) {
        if (SEEN.size() >= MAX_KEYS) {
            return false;
        }
        return SEEN.add(key);
    }

    /** テスト用。記録を全部忘れる。 */
    static void resetForTests() {
        SEEN.clear();
    }
}
