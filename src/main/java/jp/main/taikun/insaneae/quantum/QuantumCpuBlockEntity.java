package jp.main.taikun.insaneae.quantum;

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
import appeng.api.storage.MEStorage;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.AECableType;
import appeng.blockentity.ServerTickingBlockEntity;
import appeng.blockentity.grid.AENetworkBlockEntity;
import appeng.core.localization.GuiText;
import appeng.core.localization.Tooltips;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.me.helpers.MachineSource;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocator;
import jp.main.taikun.insaneae.menu.QuantumCpuMenu;
import jp.main.taikun.insaneae.quantum.batch.QuantumBatchReceipts;
import jp.main.taikun.insaneae.integration.aco.OptionalAcoBigIntegerIntegration;
import jp.main.taikun.insaneae.integration.aco.PendingOutputLedger;
import jp.main.taikun.insaneae.integration.aco.PendingOutputNbt;
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
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.math.BigInteger;

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
public class QuantumCpuBlockEntity extends AENetworkBlockEntity
        implements PatternProviderLogicHost, IUpgradeableObject, ServerTickingBlockEntity {

    /** カード無しでの 1 tick あたりの組み立て回数。 */
    public static final long BASE_CRAFTS_PER_TICK = 256L;
    /** 加速カード 1 枚あたりの倍率。 */
    public static final int MULTIPLIER_PER_CARD = 256;
    /** 加速カードの取り付け上限。256 * 256^7 で long が飽和する。 */
    public static final int MAX_ACCELERATION_CARDS = 7;
    /** アップグレード枠の総数。加速カード 7 + タスク統合カード 1。 */
    public static final int UPGRADE_SLOTS = MAX_ACCELERATION_CARDS + 1;
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
    private static final String NBT_PENDING_BIG = "pendingOutputsBig";

    /** 完成品が詰まっている間、何 tick おきに保存するか。 */
    private static final int PENDING_SAVE_INTERVAL = 20;

    private final QuantumCpuLogic logic = new QuantumCpuLogic(getMainNode(), this);
    private final IUpgradeInventory upgrades = UpgradeInventories.forMachine(
            ModBlocks.QUANTUM_CPU.get(), UPGRADE_SLOTS, this::saveChanges);
    private final IActionSource actionSource = new MachineSource(getMainNode()::getNode);

    /**
     * 組み上がったが、まだネットワークに入れていない完成品。
     * ME への挿入は 1 tick に 1 回だけまとめて行うので、その間ここに溜まる。
     */
    private final PendingOutputLedger pendingOutputs = OptionalAcoBigIntegerIntegration.createOutputLedger();

    /**
     * ACO の craftingtable batch レシート台帳。
     *
     * <p>ACO 未導入でも<b>ただの入れ物として存在する</b> (ACO の型に触れないクラスなので、
     * ここに持っても未導入環境のクラスロードに影響しない)。中身を触るのは
     * ACO 連携の Mixin だけ。</p>
     */
    private final QuantumBatchReceipts batchReceipts = new QuantumBatchReceipts();

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

    /** タスク統合カードが挿さっているか。まとめ 1 回をクラスタ予算の 1 操作として数えてよいか。 */
    public boolean isTaskFusionInstalled() {
        return upgrades.getInstalledUpgrades(ModUpgrades.TASK_FUSION_CARD.get()) > 0;
    }

    /**
     * 加速カードが満載か。1 tick のうちに同じパターンの窓を何度でも回してよい。
     *
     * <p>満載だと {@link #getCraftsPerTick()} は long の上限に張り付くので、
     * <b>予算はもう制約になっていない</b>。実際に効いているのは 1 窓あたりの long 会計
     * (完成品 {@code Long.MAX_VALUE / 出力数}、材料の取り出し) のほうなので、
     * 窓を重ねられるようにしないと BigInteger 級の注文が終わらない。</p>
     */
    public boolean isWindowRepeatEnabled() {
        return upgrades.getInstalledUpgrades(ModUpgrades.QUANTUM_ACCELERATION_CARD.get())
                >= MAX_ACCELERATION_CARDS;
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
        if (what == null || amount <= 0L) {
            return;
        }
        addPendingOutput(what, BigInteger.valueOf(amount));
    }

    /** 掛け算結果をlongへ戻さず、完成品の正確な量を台帳へ加える。 */
    public void addPendingOutput(@Nullable AEKey what, BigInteger amount) {
        if (what == null || amount == null || amount.signum() <= 0) {
            return;
        }
        pendingOutputs.add(what, amount);
    }

    /** 型の付いたロジック。{@link #getLogic()} は AE2 の基底型しか返さない。 */
    public QuantumCpuLogic getQuantumLogic() {
        return logic;
    }

    /** ACO 連携用のレシート台帳。 */
    public QuantumBatchReceipts getBatchReceipts() {
        return batchReceipts;
    }

    /** 完成品待ちの現在の中身 (コピー)。ゲームテスト用。 */
    public java.util.Map<AEKey, BigInteger> getPendingOutputs() {
        return pendingOutputs.snapshot();
    }

    @Override
    public void serverTick() {
        // 溜めておいたパターン更新をここで流す (遅れは最大 1 tick)。
        logic.flushPatternUpdate();

        if (flushPendingOutputs()) {
            savePendingIfNeeded();
        }
    }

    /**
     * 溜まっている完成品をネットワークへ流す。
     *
     * <p>毎 tick の {@link #serverTick()} からだけでなく、加速カード満載時は
     * <b>まとめ処理の窓と窓の間</b>からも呼ばれる ({@code QuantumBulkCrafting})。
     * ネットワークへ入れた瞬間にクラフト CPU が完成待ちから差し引くので、
     * これを挟むと 1 tick の合計が long を超えても帳簿が溢れない。</p>
     *
     * @return 流すものがあったか (無ければ保存も要らない)
     */
    public boolean flushPendingOutputs() {
        if (pendingOutputs.isEmpty()) {
            return false;
        }
        IGrid grid = getMainNode().getGrid();
        if (grid == null || !getMainNode().isActive()) {
            return false;
        }

        MEStorage storage = grid.getStorageService().getInventory();
        for (var entry : pendingOutputs.snapshot().entrySet()) {
            // AE2のinsertはlong APIなので、BigIntegerをこの一回分だけ安全に窓化する。
            long amount = pendingOutputs.drain(entry.getKey(), Long.MAX_VALUE);
            if (amount <= 0L) {
                continue;
            }
            long inserted = storage.insert(entry.getKey(), amount, Actionable.MODULATE, actionSource);
            if (inserted < amount) {
                // 部分搬入分だけを正確に戻し、搬入済み分を二重計上しない。
                pendingOutputs.add(entry.getKey(), BigInteger.valueOf(amount - inserted));
            }
        }
        return true;
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
    public void openMenu(Player player, MenuLocator locator) {
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

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        logic.writeToNBT(data);
        upgrades.writeToNBT(data, NBT_UPGRADES);

        // BigIntegerはbyte[]として保存する。旧ListTagはloadTag側で移行する。
        // 形式は台帳の実装 (ACO / 内蔵) に任せず、常に InsaneAE 側で固定する。
        data.put(NBT_PENDING_BIG, PendingOutputNbt.save(pendingOutputs));
        // ACO の craftingtable batch レシート。ACO 未導入でも読み書きするだけなので、
        // 一度連携で使ったワールドから ACO を抜いてもレシートは消えない。
        batchReceipts.save(data);
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        logic.readFromNBT(data);
        upgrades.readFromNBT(data, NBT_UPGRADES);

        batchReceipts.load(data);

        pendingOutputs.clear();
        if (data.contains(NBT_PENDING_BIG, Tag.TAG_COMPOUND)) {
            // 読めないエントリはスキップされる (例外は投げない)。ここで投げるとチャンク読込が壊れる。
            PendingOutputNbt.load(pendingOutputs, data.getCompound(NBT_PENDING_BIG));
        } else {
            // 旧バージョンのlong台帳を読み、最初の保存でBigInteger形式へ移行する。
            ListTag pending = data.getList(NBT_PENDING, Tag.TAG_COMPOUND);
            for (int i = 0; i < pending.size(); i++) {
                GenericStack stack = GenericStack.readTag(pending.getCompound(i));
                if (stack != null && stack.amount() > 0L) {
                    pendingOutputs.add(stack.what(), BigInteger.valueOf(stack.amount()));
                }
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
        for (var entry : pendingOutputs.snapshot().entrySet()) {
            BigInteger amount = entry.getValue();
            if (amount.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0) {
                entry.getKey().addDrops(amount.longValueExact(), drops, level, pos);
            } else {
                // AE2のドロップAPI自体がlongなので、物理ドロップへ変換できる境界を明示する。
                // 実際の大量出力は通常tickのME搬入で処理され、破壊時だけこの警告へ到達する。
                entry.getKey().addDrops(Long.MAX_VALUE, drops, level, pos);
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        logic.clearContent();
        upgrades.clear();
        pendingOutputs.clear();
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        LazyOptional<T> fromLogic = logic.getCapability(cap);
        return fromLogic.isPresent() ? fromLogic : super.getCapability(cap, side);
    }
}
