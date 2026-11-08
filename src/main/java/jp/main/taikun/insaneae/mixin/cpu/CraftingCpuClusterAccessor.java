package jp.main.taikun.insaneae.mixin.cpu;

import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.MachineSource;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@code CraftingCPUCluster} の「集計結果」フィールドへの書き込み口。
 *
 * <p>AE2はこの4つを {@code updateStatus()} の中で構成ブロックを数え上げて入れている。
 * Quantum CPUの内蔵CPUには構成ブロックが無く、代わりに内部スロットの中身から
 * 直接入れたいので、書き込み口だけを開ける ({@link jp.main.taikun.insaneae.quantum.cpu.QuantumCraftingCpu})。</p>
 *
 * <p>読み出し側 ({@code getAvailableStorage} / {@code getCoProcessors} / {@code getName} /
 * {@code getSrc}) はどれも素のフィールド返しなので、ここに入れておけばそのまま反映される。
 * 注入が要るのは「構成ブロックを辿る」3メソッドだけ
 * ({@code CraftingCpuClusterOwnerMixin})。</p>
 */
@Mixin(value = CraftingCPUCluster.class, remap = false)
public interface CraftingCpuClusterAccessor {

    /** 総バイト数。{@code getAvailableStorage()} がそのまま返す。 */
    @Accessor("storage")
    void insaneae$setStorage(long storage);

    /** 総スレッド数、つまり協調処理ユニット。{@code getCoProcessors()}がそのまま返す。 */
    @Accessor("accelerator")
    void insaneae$setAccelerator(int accelerator);

    /** CPU一覧に出る名前。{@code getName()} がそのまま返す。 */
    @Accessor("myName")
    void insaneae$setName(Component name);

    /**
     * このクラスタがMEを触るときの実行主体。
     *
     * <p>{@code getSrc()} は {@code Objects.requireNonNull} するので必ず入れること。
     * AE2は代表ブロックから作るが、こちらはQuantum CPU自身から作る。</p>
     */
    @Accessor("machineSrc")
    void insaneae$setMachineSource(MachineSource source);
}
