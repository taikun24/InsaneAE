package jp.main.taikun.insaneae.mixin;

import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.MachineSource;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@code CraftingCPUCluster} の「集計結果」フィールドへの書き込み口。
 *
 * <p>AE2 はこの 4 つを {@code updateStatus()} の中で<b>構成ブロックを数え上げて</b>入れている。
 * Quantum CPU の内蔵 CPU には構成ブロックが無く、代わりに内部スロットの中身から
 * 直接入れたいので、書き込み口だけを開ける ({@link jp.main.taikun.insaneae.quantum.cpu.QuantumCraftingCpu})。</p>
 *
 * <p>読み出し側 ({@code getAvailableStorage} / {@code getCoProcessors} / {@code getName} /
 * {@code getSrc}) は<b>どれも素のフィールド返しなので、ここに入れておけばそのまま効く</b>。
 * 注入が要るのは「構成ブロックを辿る」3 メソッドだけ
 * ({@code CraftingCpuClusterOwnerMixin})。</p>
 */
@Mixin(value = CraftingCPUCluster.class, remap = false)
public interface CraftingCpuClusterAccessor {

    /** 総バイト数。{@code getAvailableStorage()} がそのまま返す。 */
    @Accessor("storage")
    void insaneae$setStorage(long storage);

    /** 総スレッド数 (協調処理ユニット)。{@code getCoProcessors()} がそのまま返す。 */
    @Accessor("accelerator")
    void insaneae$setAccelerator(int accelerator);

    /** CPU 一覧に出る名前。{@code getName()} がそのまま返す。 */
    @Accessor("myName")
    void insaneae$setName(Component name);

    /**
     * このクラスタが ME を触るときの実行主体。
     *
     * <p>{@code getSrc()} は {@code Objects.requireNonNull} するので<b>必ず入れること</b>。
     * AE2 は代表ブロックから作るが、こちらは Quantum CPU 自身から作る。</p>
     */
    @Accessor("machineSrc")
    void insaneae$setMachineSource(MachineSource source);
}
