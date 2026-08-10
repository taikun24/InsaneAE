package jp.main.taikun.insaneae.registries;

import appeng.api.AECapabilities;
import appeng.api.implementations.items.IAEItemPowerStorage;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.items.tools.powered.powersink.PoweredItemCapabilities;
import jp.main.taikun.insaneae.InsaneAE;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * このMod が公開する capability の登録。
 *
 * <p><b>NeoForge の capability は継承されない。</b>1.20.1 の Forge では
 * {@code AEBaseBlockEntity#getCapability} を継承するだけで済んでいたが、NeoForge では
 * {@link RegisterCapabilitiesEvent} で <b>BlockEntityType ごとに</b>登録する方式になった。
 * AE2 のクラス ({@code CraftingBlockEntity} など) をそのまま使っていても、
 * <b>型が自前なら AE2 の登録には入らない</b>ので、ここで登録し直す必要がある。</p>
 *
 * <p>AE2 側は {@code InitCapabilityProviders} が
 * {@code AEBlockEntities.getImplementorsOf(IInWorldGridNodeHost.class)} を回して登録しているが、
 * これは <b>AE2 自身の BlockEntityType だけ</b>が対象。MEGA Cells も同じ理由で
 * 自分の {@code DeferredRegister} を全部回して登録している。</p>
 *
 * <p>とくに {@link AECapabilities#IN_WORLD_GRID_NODE_HOST} が抜けていると、
 * {@code GridHelper.getNodeHost} ({@code Level#getCapability} 一発) がノードを見つけられず、
 * <b>ブロックがネットワークに繋がらない</b>。「置いてもケーブルが接続しない」形で出る。</p>
 */
public final class ModCapabilities {

    private ModCapabilities() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        // グリッドノードの公開。自前の BlockEntity は全部 AENetworked(Inv)BlockEntity 派生 =
        // IInWorldGridNodeHost なので、登録漏れが起きないよう個別に並べず全部回す。
        for (BlockEntityType<?> type : ModBlockEntities.allTypes()) {
            registerGridNodeHost(event, type);
        }

        // Quantum CPU の取り出し用インベントリ (クラフト結果の戻り先)。
        // AE2 も本家パターンプロバイダを InitCapabilityProviders で同じように登録している。
        event.registerBlockEntity(
                AECapabilities.GENERIC_INTERNAL_INV,
                ModBlockEntities.QUANTUM_CPU.get(),
                (blockEntity, context) -> blockEntity.getLogic().getReturnInv());

        // 特大パターンプロバイダーも同じく、返却インベントリを外へ見せる
        // (隣接機械が完成品をここへ押し戻すための口)。
        event.registerBlockEntity(
                AECapabilities.GENERIC_INTERNAL_INV,
                ModBlockEntities.INSANE_PATTERN_PROVIDER.get(),
                (blockEntity, context) -> blockEntity.getLogic().getReturnInv());

        // 超特大インターフェイス。AE2 は自分の InterfaceBlockEntity にしか登録しないので、
        // 同じ 2 つをこちらの型にも登録する。
        //   GENERIC_INTERNAL_INV: 外の機械から見える中身。AE2 は在庫インベントリをそのまま
        //     出しているが、こちらは long でまとめて来たぶんを ME に流す包みを出す
        //     (この capability を出しておくと、AE2 と Applied Mekanistics が
        //      LOWEST 優先度で IItemHandler / IFluidHandler / IChemicalHandler の
        //      アダプタを勝手に足してくれる)。
        //   ME_STORAGE: AE2 を知っている相手向け。未設定なら ME ネットワーク直結になる。
        event.registerBlockEntity(
                AECapabilities.GENERIC_INTERNAL_INV,
                ModBlockEntities.INSANE_INTERFACE.get(),
                (blockEntity, context) -> blockEntity.getExposedInventory());
        event.registerBlockEntity(
                AECapabilities.ME_STORAGE,
                ModBlockEntities.INSANE_INTERFACE.get(),
                (blockEntity, context) -> blockEntity.getInterfaceLogic().getInventory());

        // 改良チャージャーの入出力スロット。ホッパー等から挿入・取り出しできるようにする
        // (フィルタは AppEngInternalInventory 側の IAEItemFilter がそのまま効く)。
        // AE2 は AEBaseInvBlockEntity 派生に一括でこれを登録している。
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.IMPROVED_CHARGER.get(),
                (blockEntity, side) -> blockEntity.getExposedItemHandler(side));

        // ポータブルセルの FE 受け取り口。AE2 も MEGA Cells も自分のポータブルセルに
        // 個別に登録している (自前の改良チャージャーは IAEItemPowerStorage を直接叩くので
        // これが無くても充電できるが、他 Mod の充電器や FE 表示はこの capability を見る)。
        ModCells.PORTABLE_ITEM_CELLS.values().forEach(cell -> registerPoweredItem(event, cell.get()));
        ModCells.PORTABLE_FLUID_CELLS.values().forEach(cell -> registerPoweredItem(event, cell.get()));
        if (ModList.get().isLoaded(InsaneAE.APPMEK_MODID)) {
            jp.main.taikun.insaneae.integration.appmek.AppMekCells.PORTABLE_CHEMICAL_CELLS.values()
                    .forEach(cell -> registerPoweredItem(event, cell.get()));
        }
    }

    /**
     * ワールド上のグリッドノードとして自分を公開する。
     *
     * <p>型引数を受ける別メソッドにしているのは、{@code BlockEntityType<?>} のままだと
     * {@code registerBlockEntity} の型が合わないため (ワイルドカードの捕捉)。</p>
     */
    private static <T extends BlockEntity> void registerGridNodeHost(RegisterCapabilitiesEvent event,
            BlockEntityType<T> type) {
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, type,
                (blockEntity, context) ->
                        blockEntity instanceof IInWorldGridNodeHost host ? host : null);
    }

    /** {@link IAEItemPowerStorage} なアイテムを NeoForge のエネルギー貯蔵として見せる。 */
    private static void registerPoweredItem(RegisterCapabilitiesEvent event, Item item) {
        if (item instanceof IAEItemPowerStorage powered) {
            event.registerItem(Capabilities.EnergyStorage.ITEM,
                    (stack, context) -> new PoweredItemCapabilities(stack, powered), item);
        }
    }
}
