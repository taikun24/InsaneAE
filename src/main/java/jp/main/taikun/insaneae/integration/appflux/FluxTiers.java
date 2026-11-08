package jp.main.taikun.insaneae.integration.appflux;

import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;

import java.util.Arrays;
import java.util.List;

/**
 * FEセルを出す階層と、その容量をバイト数で表す。
 *
 * <p>AppliedFluxのクラスに触らないので、datagenのモデル生成など相手が居ない場面からも引ける。</p>
 *
 * <p>FEの上限は「バイト × 1バイトあたりのFE」のlongで、AppliedFluxの設定の既定は2^20なので、
 * 既定設定では2^43バイト (8T) が限界になる。階層は1G〜16Tまでにして、
 * 最上段の16Tだけ容量を8Tにしてある。16Tのまま2^44にするとlongを溢れるため。
 * 8T × 2^20 = 2^63はlongの上限をちょうど1越えるので、実際には {@link FluxCapacity} が
 * 2バイトだけ削った容量になる。設定を上げた場合も同じくそこで頭打ちになる。
 * それより上の階層は、AppliedFluxの容量計算をlongから広げるまで出さない。広げるにはMixinなどを使う。</p>
 */
public final class FluxTiers {

    /** FEセルの最上段。 */
    public static final InsaneCraftingUnitType TOP = InsaneCraftingUnitType.STORAGE_16T;

    /** 最上段の容量 (8T)。 */
    private static final long TOP_BYTES = 1L << 43;

    public static final List<InsaneCraftingUnitType> TIERS = Arrays.stream(InsaneCraftingUnitType.values())
            .filter(tier -> tier.ordinal() <= TOP.ordinal())
            .toList();

    private FluxTiers() {
    }

    /** その階層のFEセルの容量。バイト、頭打ち前。 */
    public static long bytes(InsaneCraftingUnitType tier) {
        return tier == TOP ? TOP_BYTES : tier.getStorageBytes();
    }
}
