package jp.main.taikun.insaneae.mixin.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderTarget;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Set;

/**
 * {@link PatternProviderLogic#pushPattern} を独自実装で置き換えたときに、
 * AE2 側の状態 (クラフトロック・送信待ち・押し出し先) をそのまま使うための入口。
 *
 * <p>どれも private なのでサブクラスからは触れない。状態を自前で持ち直すと
 * AE2 本来の 1 回ずつの経路とずれるので、Mixin で穴を開けて同じものを使う。</p>
 */
@Mixin(value = PatternProviderLogic.class, remap = false)
public interface PatternProviderLogicAccessor {

    @Invoker("onPushPatternSuccess")
    void insaneae$onPushPatternSuccess(IPatternDetails pattern);

    @Invoker("getActiveSides")
    Set<Direction> insaneae$getActiveSides();

    @Invoker("findAdapter")
    PatternProviderTarget insaneae$findAdapter(Direction side);

    @Invoker("addToSendList")
    void insaneae$addToSendList(AEKey what, long amount);

    @Invoker("sendStacksOut")
    boolean insaneae$sendStacksOut();

    @Accessor("sendDirection")
    void insaneae$setSendDirection(Direction direction);

    @Accessor("roundRobinIndex")
    int insaneae$getRoundRobinIndex();

    @Accessor("roundRobinIndex")
    void insaneae$setRoundRobinIndex(int index);
}
