package jp.main.taikun.insaneae.testplots;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.items.contents.CellConfig;
import appeng.server.testworld.PlotTestHelper;
import jp.main.taikun.insaneae.registries.ModCells;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
//? if >=1.21 {
import java.util.List;
//?}

/**
 * 複数のテストプロットで使う部品。
 *
 * <p>テストプロットはAE2のプロットに相乗りする検証で、{@code appeng.tests=true} のときだけ登録される。
 * 走らせ方: {@code ./gradlew runGameTestServer}。AE2のテストも一緒に走る。
 * テスト名はAE2のアダプタ経由なので {@code ae2.insaneae_crafting_batch} のようになる。</p>
 *
 * <p>プロットクラスには {@code @TestPlotClass} を付けるだけでよい。AE2 19.2は
 * FMLのスキャンデータから自動で拾う。1.20.1とAE2 15.xのmainブランチは手動登録が必要。</p>
 */
final class TestPlotSupport {

    private TestPlotSupport() {
    }

    /** 居なければnull。ACO連携のテストをACO無しの環境でも走らせるため。 */
    @Nullable
    static Class<?> optionalClass(String name) {
        try {
            return Class.forName(name, false, TestPlotSupport.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError absent) {
            return null;
        }
    }

    /**
     * ACOがBigInteger計画を断った理由。{@code /aco stats} に出るのと同じ内容。
     *
     * <p>反射なのは、この検査クラスがACO無しの環境でも読まれるため。</p>
     */
    static String acoPlanDiagnostics() {
        try {
            Class<?> diagnostics = Class.forName(
                    "com.syaru.ae2craftingoptimizer.optimization.BigIntegerPlanDiagnostics",
                    false, TestPlotSupport.class.getClassLoader());
            Object lines = diagnostics.getMethod("summaryLines").invoke(null);
            return String.valueOf(lines);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
            return "(ACO の診断を読めない: " + unavailable + ")";
        }
    }

    /** 任意Modのブロック。そのModが入っていなければnull。 */
    @Nullable
    static Block optionalBlock(
            String namespace, String id) {
        var key = ResourceLocation.fromNamespaceAndPath(namespace, id);
        if (!BuiltInRegistries.BLOCK.containsKey(key)) {
            return null;
        }
        var block = BuiltInRegistries.BLOCK.get(key);
        return block == Blocks.AIR ? null : block;
    }

    static ItemStack ultraCreativeCell(ItemLike... contents) {
        ItemStack cell = new ItemStack(ModCells.ULTRA_CREATIVE_CELL.get());
        var config = CellConfig.create(cell);
        for (int i = 0; i < contents.length; i++) {
            config.setStack(i, new GenericStack(
                    AEItemKey.of(contents[i].asItem()), 1));
        }
        return cell;
    }

    /** ネットワーク全体に載っているアイテム数。 */
    static long storedAmount(PlotTestHelper helper,
            ItemLike item) {
        return helper.getGrid(BlockPos.ZERO).getStorageService().getInventory()
                .getAvailableStacks().get(AEItemKey.of(item.asItem()));
    }

    static ItemStack processingPattern(Item input, long inputAmount,
            Item output, long outputAmount) {
        return PatternDetailsHelper.encodeProcessingPattern(
                //? if >=1.21 {
                List.of(new GenericStack(AEItemKey.of(input), inputAmount)),
                List.of(new GenericStack(AEItemKey.of(output), outputAmount)));
                //?} else {
                /*new GenericStack[] {
                        new GenericStack(AEItemKey.of(input), inputAmount) },
                new GenericStack[] {
                        new GenericStack(AEItemKey.of(output), outputAmount) });
                *///?}
    }
}
