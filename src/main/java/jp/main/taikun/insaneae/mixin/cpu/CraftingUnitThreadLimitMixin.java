package jp.main.taikun.insaneae.mixin.cpu;

import appeng.me.cluster.implementations.CraftingCPUCluster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * {@code CraftingCPUCluster.addBlockEntity} の「1ブロックあたり16スレッド」上限を外す。
 *
 * <p>これだけをわざと低いpriorityの別Mixinに分けてある。理由:</p>
 * <ul>
 *   <li>同じ定数を狙うModが他にもいる。たとえばBiggerAE2は上限を1024にする。
 *       {@code @ModifyConstant} が衝突するとMixinは
 *       priorityが低い方を黙ってskipする ("@ModifyConstant conflict. Skipping ...")。
 *       skipされた側は {@code require} を満たせず、既定の {@code require = 1} なら
 *       {@code InjectionError}で起動ごと落ちる。1.0.0で報告されたクラッシュがこれ。</li>
 *   <li>相手の {@code require} は変えられないので、こちらが必ず譲るのが唯一の安全策。
 *       priorityを既定 (1000) より下げておけば、競合相手がいるときは常にこちらがskipされる。
 *       こちらは {@code require = 0} なので何も起きない。</li>
 *   <li>譲った結果まだ上限が残っていても、たとえばBiggerAE2の1024など、
 *       {@code CraftingCPUClusterMixin#insaneae$acceptOverLimit} が例外の直前で拾う。</li>
 *   <li>逆にInsaneAEだけを入れている場合は競合が起きないので、このMixinがそのまま適用される
 *       例外経路に入らないので、他ModのRETURN注入も素通しできる。</li>
 * </ul>
 *
 * <p>{@code CraftingCPUClusterMixin}本体は逆にpriorityを上げてある。マージ済みメソッドへ
 * 注入できるようにするため) ので、両者を1クラスにまとめることはできない。</p>
 */
@Mixin(value = CraftingCPUCluster.class, remap = false, priority = 900)
public abstract class CraftingUnitThreadLimitMixin {

    @ModifyConstant(method = "addBlockEntity", constant = @Constant(intValue = 16),
            require = 0, expect = 0)
    private int insaneae$liftThreadLimit(int original) {
        return Integer.MAX_VALUE;
    }
}
