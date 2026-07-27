package jp.main.taikun.insaneae.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@link Slot#x} / {@link Slot#y} を書き換えられるようにする。
 *
 * <p>スロットの座標は {@code final} なので普通は代入できない。AE2 はアクセストランスフォーマで
 * 外していて ({@code AEBaseScreen#repositionSlots} が直接代入している)、こちらは
 * 同じことを Mixin のアクセサでやる (AT を足すと Minecraft の再セットアップが要るため)。</p>
 *
 * <p>Quantum CPU のパターン枠のページ送りで、表示中のページのスロットだけを
 * 並べ直すのに使う → {@code client/QuantumCpuScreen}。</p>
 */
@Mixin(Slot.class)
public interface SlotAccessor {

    @Mutable
    @Accessor("x")
    void insaneae$setX(int x);

    @Mutable
    @Accessor("y")
    void insaneae$setY(int y);
}
