package jp.main.taikun.insaneae.integration.ae2things;

/**
 * InsaneAE階層のDISK。AE2 Thingsのアイテム用DISKと、AE2 MEGA Thingsの液体・化学物質用DISKが実装する。
 *
 * <p>どちらも中身はAE2 Thingsの {@code DISKCellInventory} (MEGA Thingsはその子クラス) が扱い、
 * 容量はintで止まるので、{@code DiskCellInventoryMixin} と {@code DiskCellExtractMixin} が
 * これを目印にlongへ広げる。このインターフェイス自体は相手のクラスを参照しない。</p>
 */
public interface InsaneDisk {

    /** 入るバイト数。アイテムは1個、液体・化学物質は1000 mBが1バイト。 */
    long capacity();
}
