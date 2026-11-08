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
 * <p>ここが壊れるとQuantum CPUのGUIを開いた人1人につき毎tick 1620枠ぶんの
 * スロット同期がサーバに乗る。Mixinで窓に差し替えるとExtendedAE Plus 1.6の
 * {@code @Redirect} に取られて外れるため、受け渡しで行っている)。
 * 窓の有効範囲が広がりすぎていないかも見る。</p>
 *
 * <p>本物のインベントリは用意しない。単体テストのクラスパスにMinecraft本体は無く、
 * {@code InternalInventory}を実装したり、{@code Proxy}化したりしようとすると
 * {@code ItemStack} の初期化とレジストリのブートストラップが必要になる。
 * この受け渡しは中身を一切触らないので {@code null} を通しても意味は変わらない。
 * 見るのは「窓 ({@link PagedPatternInventory}) を返したか、渡されたものをそのまま返したか」だけ。</p>
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
        // 同じ組み立ての2回目は同じ窓。AE2が何度呼んでもスロットは1セット
        assertSame(first, PatternPagingHandshake.windowFor(host, null));

        assertSame(first, PatternPagingHandshake.finish());
    }

    @Test
    void nothingIsPagedOutsideMenuConstruction() {
        Object host = host();

        // beginしていない = 通常のパターン出し入れ。渡したものがそのまま返る
        assertNull(PatternPagingHandshake.windowFor(host, null));
        assertNull(PatternPagingHandshake.finish());

        // 組み立てが終わった後も全枠に戻る
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

        // 前のコンストラクタが例外で抜けてfinish() を呼べなかった場合
        PatternPagingHandshake.begin(second, true);
        InternalInventory fresh = PatternPagingHandshake.windowFor(second, null);
        assertNotSame(abandoned, fresh);
        assertSame(fresh, PatternPagingHandshake.finish());
    }

    /**
     * ホストの代わり。{@link PatternPagingHandshake} は同一性しか見ないので、
     * {@code PatternProviderLogicHost} を用意する必要は無い。
     */
    private static Object host() {
        return new Object();
    }
}
