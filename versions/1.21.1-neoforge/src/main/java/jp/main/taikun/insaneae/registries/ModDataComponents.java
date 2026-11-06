package jp.main.taikun.insaneae.registries;

import jp.main.taikun.insaneae.InsaneAE;
import jp.main.taikun.insaneae.cell.storage.CellSummary;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.UUID;

public final class ModDataComponents {
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, InsaneAE.MODID);

    /** 中身を {@code CellContentStore} から引くためのキー。空のセルには付かない。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> CELL_ID =
            COMPONENTS.register("cell_id", () -> DataComponentType.<UUID>builder()
                    .persistent(UUIDUtil.CODEC)
                    .networkSynchronized(UUIDUtil.STREAM_CODEC)
                    .build());

    /**
     * クライアントにはワールドの保存データが無いので、ツールチップと LED に要る分だけを
     * アイテム側に写しておく。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CellSummary>> CELL_SUMMARY =
            COMPONENTS.register("cell_summary", () -> DataComponentType.<CellSummary>builder()
                    .persistent(CellSummary.CODEC)
                    .networkSynchronized(CellSummary.STREAM_CODEC)
                    .build());

    private ModDataComponents() {
    }

    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
    }
}
