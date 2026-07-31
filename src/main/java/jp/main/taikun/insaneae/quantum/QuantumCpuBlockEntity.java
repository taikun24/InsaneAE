package jp.main.taikun.insaneae.quantum;

import jp.main.taikun.insaneae.registries.ModBlockEntities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.minecraft.core.HolderLookup;
import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.AECableType;
import appeng.blockentity.ServerTickingBlockEntity;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.core.localization.GuiText;
import appeng.core.localization.Tooltips;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.me.helpers.MachineSource;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import jp.main.taikun.insaneae.menu.QuantumCpuMenu;
import jp.main.taikun.insaneae.registries.ModBlocks;
import jp.main.taikun.insaneae.registries.ModUpgrades;
import jp.main.taikun.insaneae.upgrade.SpeedBoost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * Quantum CPU — パターンプロバイダと分子組立装置を 1 ブロックに合体させたもの。
 *
 * <p>ネットワークからはパターンプロバイダとして見えるが、クラフトテーブル用パターンを
 * 隣の分子組立装置に渡すのではなく<b>自分で組み立てて完成品を ME に戻す</b>。
 * 加工パターンは普通のパターンプロバイダとして隣接インベントリへ押し出す。</p>
 *
 * <p>素の速度は {@link #BASE_CRAFTS_PER_TICK} クラフト/tick。
 * {@link ModUpgrades#QUANTUM_ACCELERATION_CARD 加速カード} 1 枚ごとに
 * {@link #MULTIPLIER_PER_CARD} 倍になり、{@link #MAX_ACCELERATION_CARDS} 枚で long の上限に達する。</p>
 */
public class QuantumCpuBlockEntity extends AENetworkedBlockEntity
        implements PatternProviderLogicHost, IUpgradeableObject, ServerTickingBlockEntity {

    /** カード無しでの 1 tick あたりの組み立て回数。 */
    public static final long BASE_CRAFTS_PER_TICK = 256L;
    /** 加速カード 1 枚あたりの倍率。 */
    public static final int MULTIPLIER_PER_CARD = 256;
    /** 加速カードの取り付け上限。256 * 256^7 で long が飽和する。 */
    public static final int MAX_ACCELERATION_CARDS = 7;
    /** パターンスロットの 1 ページぶんの列数。 */
    public static final int PATTERN_COLUMNS = 9;
    /** パターンスロットの 1 ページぶんの行数。 */
    public static final int PATTERN_ROWS = 6;
    /** パターンスロットのページ数。 */
    public static final int PATTERN_PAGES = 30;
    /** 1 ページぶんのスロット数。 */
    public static final int PATTERN_SLOTS_PER_PAGE = PATTERN_COLUMNS * PATTERN_ROWS;
    /**
     * パターンスロット数。9×6 を 30 ページで 1620 枠。
     *
     * <p>画面は 1 ページぶんしか描かないが、メニューには全スロットが並んでいる
     * (ページ送りは {@code QuantumCpuScreen} が表示スロットを切り替えるだけの<b>クライアント処理</b>で、
     * サーバとのやり取りは無い)。そのぶん GUI を開いた瞬間の同期パケットは大きくなる。</p>
     */
    public static final int PATTERN_SLOTS = PATTERN_SLOTS_PER_PAGE * PATTERN_PAGES;

    private static final String NBT_UPGRADES = "upgrades";
    private static final String NBT_PENDING = "pendingOutputs";

    /** 完成品が詰まっている間、何 tick おきに保存するか。 */
    private static final int PENDING_SAVE_INTERVAL = 20;

    private final QuantumCpuLogic logic = new QuantumCpuLogic(getMainNode(), this);
    private final IUpgradeInventory upgrades = UpgradeInventories.forMachine(
            ModBlocks.QUANTUM_CPU.get(), MAX_ACCELERATION_CARDS, this::saveChanges);
    private final IActionSource actionSource = new MachineSource(getMainNode()::getNode);

    /**
     * 組み上がったが、まだネットワークに入れていない完成品。
     * ME への挿入は 1 tick に 1 回だけまとめて行うので、その間ここに溜まる。
     */
    private final KeyCounter pendingOutputs = new KeyCounter();

    /** 直近の保存時点で {@link #pendingOutputs} が空でなかったか。 */
    private boolean pendingWasSaved;
    /** 詰まっている状態を最後に保存してからの tick 数。 */
    private int ticksSincePendingSave;

    public QuantumCpuBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        getMainNode().setIdlePowerUsage(6.0);
    }

    // ---------------------------------------------------------------- 速度

    /** 現在の 1 tick あたりの組み立て回数。 */
    public long getCraftsPerTick() {
        int cards = upgrades.getInstalledUpgrades(ModUpgrades.QUANTUM_ACCELERATION_CARD.get());
        long speed = BASE_CRAFTS_PER_TICK;
        for (int i = 0; i < cards; i++) {
            speed = SpeedBoost.saturatingMultiply(speed, MULTIPLIER_PER_CARD);
        }
        return speed;
    }

    // ------------------------------------------------------- 完成品の受け渡し

    /**
     * 完成品・端材を溜める。実際の ME への挿入は {@link #serverTick()} でまとめて行う。
     *
     * <p>ここで {@code alertDevice} は呼ばない。このブロックはブロック側の ticker で
     * <b>毎 tick 必ず動く</b>ので起こしてもらう必要が無く、
     * 呼ぶとクラフト 1 回ごとにグリッドのティックキューを並べ替えることになる
     * ({@code TickManagerService#alertDevice} は {@code updateQueuePosition} まで走る)。</p>
     */
    void addPendingOutput(@Nullable AEKey what, long amount) {
        if (what != null && amount > 0) {
            pendingOutputs.add(what, amount);
        }
    }

    @Override
    public void serverTick() {
        // 溜めておいたパターン更新をここで流す (遅れは最大 1 tick)。
        logic.flushPatternUpdate();

        if (pendingOutputs.isEmpty()) {
            return;
        }
        IGrid grid = getMainNode().getGrid();
        if (grid == null || !getMainNode().isActive()) {
            return;
        }

        MEStorage storage = grid.getStorageService().getInventory();
        for (var entry : pendingOutputs) {
            long amount = entry.getLongValue();
            if (amount <= 0) {
                continue;
            }
            long inserted = storage.insert(entry.getKey(), amount, Actionable.MODULATE, actionSource);
            if (inserted > 0) {
                entry.setValue(amount - inserted);
            }
        }
        pendingOutputs.removeZeros();
        savePendingIfNeeded();
    }

    /**
     * 溜まっている完成品をディスクに残す必要があるときだけ {@code saveChanges()} する。
     *
     * <p>{@code saveChanges()} はチャンクに dirty を立てるので、
     * 毎 tick 呼ぶと<b>オートセーブのたびに 1620 枠ぶんのパターン NBT を書き出す</b>ことになる。
     * 普段は溜めた完成品をその tick のうちに全部 ME に入れられる = 保存すべき内容が
     * 「空のまま」で変わらないので、保存自体が要らない。</p>
     *
     * <p>入りきらずに残っている間だけ保存し (詰まっている間は {@value #PENDING_SAVE_INTERVAL} tick に 1 回)、
     * 捌けきったら「空になった」ことを 1 回だけ保存する。</p>
     */
    private void savePendingIfNeeded() {
        if (!pendingOutputs.isEmpty()) {
            if (!pendingWasSaved || ++ticksSincePendingSave >= PENDING_SAVE_INTERVAL) {
                pendingWasSaved = true;
                ticksSincePendingSave = 0;
                saveChanges();
            }
        } else if (pendingWasSaved) {
            pendingWasSaved = false;
            ticksSincePendingSave = 0;
            saveChanges();
        }
    }

    // ------------------------------------------------- PatternProviderLogicHost

    @Override
    public PatternProviderLogic getLogic() {
        return logic;
    }

    @Override
    public EnumSet<Direction> getTargets() {
        // 加工パターンのフォールバック用。全方向に押し出せる。
        return EnumSet.allOf(Direction.class);
    }

    @Override
    public AEItemKey getTerminalIcon() {
        return AEItemKey.of(ModBlocks.QUANTUM_CPU.get());
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(ModBlocks.QUANTUM_CPU.get());
    }

    /**
     * パターンアクセス端末での表示。隣接機械ではなく自分で組むので、
     * 「隣は何の機械か」ではなく自分自身と速度を出す。
     */
    @Override
    public PatternContainerGroup getTerminalGroup() {
        AEItemKey icon = getTerminalIcon();
        Component name = hasCustomName() ? getCustomName() : icon.getDisplayName();
        int cards = upgrades.getInstalledUpgrades(ModUpgrades.QUANTUM_ACCELERATION_CARD.get());
        List<Component> tooltip = cards == 0
                ? List.of()
                : List.of(GuiText.CompatibleUpgrade.text(
                        Tooltips.of(ModUpgrades.QUANTUM_ACCELERATION_CARD.get().getDescription()),
                        Tooltips.ofUnformattedNumber(cards)));
        return new PatternContainerGroup(icon, name, tooltip);
    }

    @Override
    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(QuantumCpuMenu.TYPE, player, locator);
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.returnTo(QuantumCpuMenu.TYPE, player, subMenu.getLocator());
    }

    // ------------------------------------------------------------ アップグレード

    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        return id.equals(ISegmentedInventory.UPGRADES) ? upgrades : super.getSubInventory(id);
    }

    // ------------------------------------------------------------ BlockEntity

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        logic.onMainNodeStateChanged();
    }

    @Override
    public void onReady() {
        super.onReady();
        logic.updatePatterns();
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }

    // 1.20.5 以降、NBT の読み書きにはレジストリ参照 (HolderLookup.Provider) が要る。
    // データコンポーネントを含む ItemStack をタグ化するのにレジストリが必要になったため。
    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        logic.writeToNBT(data, registries);
        upgrades.writeToNBT(data, NBT_UPGRADES, registries);

        ListTag pending = new ListTag();
        for (var entry : pendingOutputs) {
            if (entry.getLongValue() > 0) {
                pending.add(GenericStack.writeTag(registries,
                        new GenericStack(entry.getKey(), entry.getLongValue())));
            }
        }
        data.put(NBT_PENDING, pending);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        logic.readFromNBT(data, registries);
        upgrades.readFromNBT(data, NBT_UPGRADES, registries);

        pendingOutputs.reset();
        ListTag pending = data.getList(NBT_PENDING, Tag.TAG_COMPOUND);
        for (int i = 0; i < pending.size(); i++) {
            GenericStack stack = GenericStack.readTag(registries, pending.getCompound(i));
            if (stack != null) {
                pendingOutputs.add(stack.what(), stack.amount());
            }
        }
        // 読み込んだ時点の中身は「保存済み」。空になったときに 1 回だけ保存すればよい。
        pendingWasSaved = !pendingOutputs.isEmpty();
        ticksSincePendingSave = 0;
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        logic.addDrops(drops);
        for (ItemStack upgrade : upgrades) {
            drops.add(upgrade);
        }
        for (var entry : pendingOutputs) {
            entry.getKey().addDrops(entry.getLongValue(), drops, level, pos);
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        logic.clearContent();
        upgrades.clear();
        pendingOutputs.reset();
    }

    /**
     * 取り出し用インベントリ (クラフト結果の戻り先) を capability として公開する。
     *
     * <p>1.20.1 では {@code getCapability} を override して
     * {@code PatternProviderLogic#getCapability} に委譲していたが、
     * NeoForge では capability は BlockEntityType ごとに
     * {@link RegisterCapabilitiesEvent} で登録する方式に変わった。
     * AE2 も本家パターンプロバイダを {@code InitCapabilityProviders} で同じように登録している。</p>
     */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                AECapabilities.GENERIC_INTERNAL_INV,
                ModBlockEntities.QUANTUM_CPU.get(),
                (blockEntity, context) -> blockEntity.getLogic().getReturnInv());
    }
}
