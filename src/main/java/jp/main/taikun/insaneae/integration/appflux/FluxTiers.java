package jp.main.taikun.insaneae.integration.appflux;

import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;

import java.util.Arrays;
import java.util.List;

/**
 * FE セルを出す階層と、その容量 (バイト)。
 *
 * <p>AppliedFlux のクラスに触らないので、datagen のモデル生成など相手が居ない場面からも引ける。</p>
 *
 * <p>FE の上限は「バイト × 1 バイトあたりの FE (AppliedFlux の設定)」の long。
 * 階層と容量は 1.21.1 版と揃えて 1G〜16T、<b>最上段の 16T だけ容量を 8T</b> にしてある
 * (1.21.1 版の既定 2^20 FE/バイトでちょうど long の際)。
 * <b>1.20.1 版 AppliedFlux の既定は 4 倍の 2^22 FE/バイト</b>なので、ここでは 2T で long に
 * 届き、4T と 16T は {@link FluxCapacity} が同じ容量 (約 922 京 FE) に頭打ちにする。
 * FE で数えた上限は両バージョンで同じ。
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
