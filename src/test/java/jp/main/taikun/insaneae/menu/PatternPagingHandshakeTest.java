package jp.main.taikun.insaneae.menu;

import appeng.api.inventories.InternalInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * メニュー組み立て中だけパターン枠を窓にすり替える受け渡しの検証。
 *
 * <p>ここが壊れると Quantum CPU の GUI を開いた人 1 人につき毎 tick 1620 枠ぶんの
 * スロット同期がサーバに乗る (以前は Mixin でやっていて、ExtendedAE Plus 1.6 の
 * {@code @Redirect} に取られて黙って外れた)。窓の有効範囲が広がりすぎていないかも見る。</p>
 *
 * <p><b>本物のインベントリは用意しない。</b>単体テストのクラスパスに Minecraft 本体は無く、
 * {@code InternalInventory} を実装 (や {@code Proxy} 化) しようとすると
 * {@code ItemStack} の初期化 → レジストリのブートストラップまで引きずり込まれる。
 * この受け渡しは中身を一切触らないので {@code null} を通しても意味は変わらない —
 * 「窓 ({@link PagedPatternInventory}) を返したか、渡されたものをそのまま返したか」だけを見る。</p>
 */
class PatternPagingHandshakeTest {

    /** 取りこぼしが次のテストに漏れないように必ず畳む。 */
    @AfterEach
    void clearPending() {
        PatternPagingHandshake.finish();
    }

    @Test
    void windowIsCreatedOnceAndHandedToTheMenu() {
        Object host = host();

        assertSame(host, PatternPagingHandshake.begin(host, true));

        InternalInventory first = PatternPagingHandshake.windowFor(host, null);
        assertTrue(first instanceof PagedPatternInventory);
        // 同じ組み立ての 2 回目は同じ窓 (AE2 が何度呼んでもスロットは 1 セット)。
        assertSame(first, PatternPagingHandshake.windowFor(host, null));

        assertSame(first, PatternPagingHandshake.finish());
    }

    @Test
    void nothingIsPagedOutsideMenuConstruction() {
        Object host = host();

        // begin していない = 通常のパターン出し入れ。渡したものがそのまま返る。
        assertNull(PatternPagingHandshake.windowFor(host, null));
        assertNull(PatternPagingHandshake.finish());

        // 組み立てが終わった後も全枠に戻る。
        PatternPagingHandshake.begin(host, true);
        PatternPagingHandshake.windowFor(host, null);
        PatternPagingHandshake.finish();
        assertNull(PatternPagingHandshake.windowFor(host, null));
    }

    @Test
    void pagingDisabledLeavesEveryPatternSlot() {
        Object host = host();

        PatternPagingHandshake.begin(host, false);
        assertNull(PatternPagingHandshake.windowFor(host, null));
        assertNull(PatternPagingHandshake.finish());
    }

    @Test
    void otherProvidersOnTheSameThreadAreNotPaged() {
        Object opened = host();
        Object other = host();

        PatternPagingHandshake.begin(opened, true);
        assertNull(PatternPagingHandshake.windowFor(other, null));
    }

    @Test
    void anAbandonedHandshakeIsNotInheritedByTheNextMenu() {
        Object first = host();
        Object second = host();

        PatternPagingHandshake.begin(first, true);
        InternalInventory abandoned = PatternPagingHandshake.windowFor(first, null);

        // 前のコンストラクタが例外で抜けて finish() を呼べなかった場合。
        PatternPagingHandshake.begin(second, true);
        InternalInventory fresh = PatternPagingHandshake.windowFor(second, null);
        assertNotSame(abandoned, fresh);
        assertSame(fresh, PatternPagingHandshake.finish());
    }

    /**
     * ホストの代わり。{@link PatternPagingHandshake} は<b>同一性しか見ない</b>ので、
     * {@code PatternProviderLogicHost} を用意する必要は無い。
     */
    private static Object host() {
        return new Object();
    }
}
