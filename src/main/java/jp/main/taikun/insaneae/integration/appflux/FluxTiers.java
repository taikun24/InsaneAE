package jp.main.taikun.insaneae.integration.appflux;

import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;

import java.util.Arrays;
import java.util.List;

/**
 * FE セルを出す階層と、その容量 (バイト)。
 *
 * <p>AppliedFlux のクラスに触らないので、datagen のモデル生成など相手が居ない場面からも引ける。</p>
 *
 * <p>FE の上限は「バイト × 1 バイトあたりの FE (AppliedFlux の設定、既定 2^20)」の long なので、
 * 既定設定では 2^43 バイト (8T) が限界になる。階層は 1G〜16T までにして、
 * <b>最上段の 16T だけ容量を 8T にしてある</b> (16T のまま 2^44 にすると long を溢れる)。
 * 8T × 2^20 = 2^63 は long の上限をちょうど 1 越えるので、実際には {@link FluxCapacity} が
 * 2 バイトだけ削った容量になる。設定を上げた場合も同じくそこで頭打ちになる。
 * それより上の階層は、AppliedFlux の容量計算を long から広げる (Mixin 等) まで出さない。</p>
 */
public final class FluxTiers {

    /** FE セルの最上段。 */
    public static final InsaneCraftingUnitType TOP = InsaneCraftingUnitType.STORAGE_16T;

    /** 最上段の容量 (8T)。 */
    private static final long TOP_BYTES = 1L << 43;

    public static final List<InsaneCraftingUnitType> TIERS = Arrays.stream(InsaneCraftingUnitType.values())
            .filter(tier -> tier.ordinal() <= TOP.ordinal())
            .toList();

    private FluxTiers() {
    }

    /** その階層の FE セルの容量 (バイト、頭打ち前)。 */
    public static long bytes(InsaneCraftingUnitType tier) {
        return tier == TOP ? TOP_BYTES : tier.getStorageBytes();
    }
}
