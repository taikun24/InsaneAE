package jp.main.taikun.insaneae.registries;

import appeng.api.AECapabilities;
import appeng.api.implementations.items.IAEItemPowerStorage;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.parts.RegisterPartCapabilitiesEvent;
import appeng.items.tools.powered.powersink.PoweredItemCapabilities;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import jp.main.taikun.insaneae.iface.InsaneInterfacePart;
import jp.main.taikun.insaneae.integration.AddonIntegrations;
import jp.main.taikun.insaneae.provider.InsanePatternProviderPart;

/**
 * このModが公開するcapabilityの登録。
 *
 * <p>NeoForgeのcapabilityは継承されない。1.20.1のForgeでは
 * {@code AEBaseBlockEntity#getCapability} を継承するだけで済んでいたが、NeoForgeでは
 * {@link RegisterCapabilitiesEvent} でBlockEntityTypeごとに登録する方式になった。
 * AE2のクラス、たとえば{@code CraftingBlockEntity}をそのまま使っていても、
 * 型が自前ならAE2の登録には入らないので、ここで登録し直す必要がある。</p>
 *
 * <p>AE2側は {@code InitCapabilityProviders} が
 * {@code AEBlockEntities.getImplementorsOf(IInWorldGridNodeHost.class)} を回して登録しているが、
 * これはAE2自身のBlockEntityTypeだけが対象。MEGA Cellsも同じ理由で
 * 自分の {@code DeferredRegister} を全部回して登録している。</p>
 *
 * <p>とくに {@link AECapabilities#IN_WORLD_GRID_NODE_HOST} が抜けていると、
 * {@code GridHelper.getNodeHost}は{@code Level#getCapability}を一度引くだけなので、ノードを見つけられず、
 * ブロックがネットワークに繋がらない。「置いてもケーブルが接続しない」という症状になる。</p>
 */
public final class ModCapabilities {

    private ModCapabilities() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        // グリッドノードの公開。自前のBlockEntityは全部AENetworked(Inv)BlockEntity派生 =
        // IInWorldGridNodeHostなので、登録漏れが起きないよう個別に並べず全部回す
        for (BlockEntityType<?> type : ModBlockEntities.allTypes()) {
            registerGridNodeHost(event, type);
        }

        // Quantum CPUの取り出し用インベントリ。クラフト結果の戻り先
        // AE2も本家パターンプロバイダをInitCapabilityProvidersで同じように登録している
        event.registerBlockEntity(
                AECapabilities.GENERIC_INTERNAL_INV,
                ModBlockEntities.QUANTUM_CPU.get(),
                (blockEntity, context) -> blockEntity.getLogic().getReturnInv());

        // 特大パターンプロバイダーも同じく、返却インベントリを外へ見せる
        // 隣接機械が完成品をここへ押し戻すための口
        event.registerBlockEntity(
                AECapabilities.GENERIC_INTERNAL_INV,
                ModBlockEntities.INSANE_PATTERN_PROVIDER.get(),
                (blockEntity, context) -> blockEntity.getLogic().getReturnInv());

        // 超特大インターフェイス。AE2は自分のInterfaceBlockEntityにしか登録しないので、
        // 同じ2つをこちらの型にも登録する
        //   GENERIC_INTERNAL_INV: 外の機械から見える中身。AE2は在庫インベントリをそのまま
        //     出しているが、こちらはlongでまとめて来たぶんをMEに流す包みを出す
        //     このcapabilityを出しておくと、AE2とApplied Mekanisticsが
        //      LOWEST優先度でIItemHandler / IFluidHandler / IChemicalHandlerの
        //      アダプタを勝手に足してくれる)
        //   ME_STORAGE: AE2を知っている相手向け。未設定ならMEネットワーク直結になる
        event.registerBlockEntity(
                AECapabilities.GENERIC_INTERNAL_INV,
                ModBlockEntities.INSANE_INTERFACE.get(),
                (blockEntity, context) -> blockEntity.getExposedInventory());
        event.registerBlockEntity(
                AECapabilities.ME_STORAGE,
                ModBlockEntities.INSANE_INTERFACE.get(),
                (blockEntity, context) -> blockEntity.getInterfaceLogic().getInventory());

        // 改良チャージャーの入出力スロット。ホッパー等から挿入・取り出しできるようにする
        // フィルタはAppEngInternalInventory側のIAEItemFilterがそのまま効く
        // AE2はAEBaseInvBlockEntity派生に一括でこれを登録している
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.IMPROVED_CHARGER.get(),
                (blockEntity, side) -> blockEntity.getExposedItemHandler(side));

        // ポータブルセルのFE受け取り口。AE2もMEGA Cellsも自分のポータブルセルに
        // 個別に登録している。自前の改良チャージャーはIAEItemPowerStorageを直接叩くので
        // これが無くても充電できるが、他Modの充電器やFE表示はこのcapabilityを見る)
        ModCells.PORTABLE_ITEM_CELLS.values().forEach(cell -> registerPoweredItem(event, cell.get()));
        ModCells.PORTABLE_FLUID_CELLS.values().forEach(cell -> registerPoweredItem(event, cell.get()));

        // 他アドオンのセル、化学物質セルやFEセルなどは、各連携が自分で登録する
        AddonIntegrations.active().forEach(addon -> addon.registerCapabilities(event));
    }

    /**
     * ケーブルに貼る版、つまり部品のcapability。
     *
     * <p>AE2の部品のcapabilityは部品のクラスの完全一致で引かれる
     * {@code RegisterPartCapabilitiesEvent}の中で{@code parts.get(part.getClass())}を使う。
     * {@code InterfacePart} / {@code PatternProviderPart} 向けのAE2の登録は、
     * それを継承したこちらの部品には効かないので、同じものを登録し直す。
     * これが無いと、パイプや他Modの機械から部品の中身が見えない。</p>
     *
     * <p>中身はブロック版と同じ。{@code GENERIC_INTERNAL_INV} を出しておけば、
     * AE2 / Applied Mekanistics / AppliedFluxがケーブルバス越しに
     * IItemHandler / IFluidHandler / 化学物質 / FEのアダプタを足してくれる
     * AppliedFluxの誘導カードの受電もこの口を通る。</p>
     */
    public static void registerParts(RegisterPartCapabilitiesEvent event) {
        event.register(AECapabilities.GENERIC_INTERNAL_INV,
                (part, context) -> part.getExposedInventory(), InsaneInterfacePart.class);
        event.register(AECapabilities.ME_STORAGE,
                (part, context) -> part.getInterfaceLogic().getInventory(), InsaneInterfacePart.class);
        event.register(AECapabilities.GENERIC_INTERNAL_INV,
                (part, context) -> part.getLogic().getReturnInv(), InsanePatternProviderPart.class);
    }

    /**
     * ワールド上のグリッドノードとして自分を公開する。
     *
     * <p>型引数を受ける別メソッドにしているのは、{@code BlockEntityType<?>} のままだと
     * {@code registerBlockEntity}の型が合わないため。ワイルドカードの捕捉が原因。</p>
     */
    private static <T extends BlockEntity> void registerGridNodeHost(RegisterCapabilitiesEvent event,
            BlockEntityType<T> type) {
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, type,
                (blockEntity, context) ->
                        blockEntity instanceof IInWorldGridNodeHost host ? host : null);
    }

    /** {@link IAEItemPowerStorage} なアイテムをNeoForgeのエネルギー貯蔵として見せる。 */
    public static void registerPoweredItem(RegisterCapabilitiesEvent event, Item item) {
        if (item instanceof IAEItemPowerStorage powered) {
            event.registerItem(Capabilities.EnergyStorage.ITEM,
                    (stack, context) -> new PoweredItemCapabilities(stack, powered), item);
        }
    }
}
