package jp.main.taikun.insaneae.mixin;

import appeng.me.cluster.implementations.CraftingCPUCluster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * {@code CraftingCPUCluster.addBlockEntity} の「1 ブロックあたり 16 スレッド」上限を外す。
 *
 * <p>これだけを<b>わざと低い priority の別 Mixin に分けてある</b>。理由:</p>
 * <ul>
 *   <li>同じ定数を狙う Mod が他にもいる (例: BiggerAE2 は上限を 1024 にする)。
 *       {@code @ModifyConstant} が衝突すると Mixin は
 *       <b>priority が低い方を黙って skip する</b> ("@ModifyConstant conflict. Skipping ...")。
 *       skip された側は {@code require} を満たせず、既定の {@code require = 1} なら
 *       {@code InjectionError} で<b>起動ごと落ちる</b> (1.0.0 で報告されたクラッシュがこれ)。</li>
 *   <li>相手の {@code require} は変えられないので、<b>こちらが必ず譲る</b>のが唯一の安全策。
 *       priority を既定 (1000) より下げておけば、競合相手がいるときは常にこちらが skip される。
 *       こちらは {@code require = 0} なので何も起きない。</li>
 *   <li>譲った結果まだ上限が残っていても (BiggerAE2 の 1024 など)、
 *       {@code CraftingCPUClusterMixin#insaneae$acceptOverLimit} が例外の直前で拾う。</li>
 *   <li>逆に InsaneAE だけを入れている場合は競合が起きないので、ここが普通に効く
 *       (＝例外経路に入らない = 他 Mod の RETURN 注入も素通しできる)。</li>
 * </ul>
 *
 * <p>{@code CraftingCPUClusterMixin} 本体は逆に priority を上げてある (マージ済みメソッドへ
 * 注入できるようにするため) ので、両者を 1 クラスにまとめることはできない。</p>
 */
@Mixin(value = CraftingCPUCluster.class, remap = false, priority = 900)
public abstract class CraftingUnitThreadLimitMixin {

    @ModifyConstant(method = "addBlockEntity", constant = @Constant(intValue = 16),
            require = 0, expect = 0)
    private int insaneae$liftThreadLimit(int original) {
        return Integer.MAX_VALUE;
    }
}
