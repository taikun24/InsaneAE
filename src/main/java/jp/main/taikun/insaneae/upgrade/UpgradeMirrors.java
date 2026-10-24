package jp.main.taikun.insaneae.upgrade;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import jp.main.taikun.insaneae.registries.ModBlocks;
import jp.main.taikun.insaneae.registries.ModParts;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * AE2 の機械に登録されたアップグレードカードを、超次元版の機械にもそのまま登録する。
 *
 * <h2>なぜ要るのか</h2>
 * <p>カードの対応付けは AE2 本体も他アドオンも {@code Upgrades.add(card, AEBlocks.INTERFACE, ...)}
 * のように<b>AE2 の機械のアイテムを名指し</b>で行う (AE2 のクラフトカード・あいまいカード、
 * AppliedFlux の誘導カードなど)。超次元版はアイテムが別なので、そのままでは 1 枚も挿せない。
 * アドオンごとに登録を書き足す代わりに、{@code Upgrades.add} 自体を見張って
 * ({@code mixin/UpgradesMixin})、写し元への登録をこちらにも複製する。</p>
 *
 * <h2>挿したカードが効くかどうか</h2>
 * <p>超次元版の中身は AE2 のクラス ({@code InterfaceLogic} / {@code PatternProviderLogic} の派生)
 * なので、<b>そのクラスにカードの処理を持たせているアドオンなら挿すだけで動く</b>
 * (AppliedFlux の誘導カードはこの形)。ブロックや部品のクラス
 * ({@code PatternProviderBlockEntity} など) に処理を足しているアドオンのカードは、
 * 挿せても効かないことがある。そういうものは連携 ({@code integration/}) で個別に補う。</p>
 *
 * <h2>登録の順番</h2>
 * <p>{@code Upgrades.add} は各 Mod の commonSetup から呼ばれ、こちらより先に来ることも後に来ることもある。
 * {@link #enable()} より前の呼び出しは控えておいて、{@link #enable()} でまとめて複製する。
 * それ以降の呼び出しはその場で複製する。</p>
 */
public final class UpgradeMirrors {

    /** {@link #enable()} より前に来た登録の控え。 */
    private record Added(Item card, Item machine, int maxSupported, @Nullable String tooltipGroup) {
    }

    private static final List<Added> PENDING = new ArrayList<>();

    /** 写し元 → 写し先。{@link #enable()} までは null。 */
    @Nullable
    private static Map<Item, List<Item>> mirrors;

    private UpgradeMirrors() {
    }

    /**
     * 写し元と写し先の組。ブロックはブロックへ、部品は部品へ写す
     * (AE2 自身もブロック版と部品版に同じカードを別々に登録している)。
     */
    private static Map<Item, List<Item>> buildMirrors() {
        Map<Item, List<Item>> table = new IdentityHashMap<>();
        table.put(AEBlocks.INTERFACE.asItem(), List.of(ModBlocks.INSANE_INTERFACE.get().asItem()));
        table.put(AEParts.INTERFACE.asItem(), List.of(ModParts.INSANE_INTERFACE.get()));
        table.put(AEBlocks.PATTERN_PROVIDER.asItem(), List.of(ModBlocks.INSANE_PATTERN_PROVIDER.get().asItem()));
        table.put(AEParts.PATTERN_PROVIDER.asItem(), List.of(ModParts.INSANE_PATTERN_PROVIDER.get()));
        return table;
    }

    /**
     * {@code Upgrades.add} の直後に呼ばれる ({@code mixin/UpgradesMixin})。
     * 写し元の機械への登録なら、写し先にも同じ内容で登録する。
     */
    public static synchronized void onUpgradeAdded(ItemLike card, ItemLike machine, int maxSupported,
            @Nullable String tooltipGroup) {
        Item cardItem = card.asItem();
        Item machineItem = machine.asItem();
        if (mirrors == null) {
            PENDING.add(new Added(cardItem, machineItem, maxSupported, tooltipGroup));
            return;
        }
        mirror(cardItem, machineItem, maxSupported, tooltipGroup);
    }

    /**
     * 複製を始める。超次元版のアイテムが揃った後 (commonSetup) に 1 回呼ぶ。
     * それまでに来ていた登録もここで複製する。
     */
    public static synchronized void enable() {
        if (mirrors != null) {
            return;
        }
        mirrors = buildMirrors();
        // 複製の Upgrades.add もこのクラスに戻ってくるが、写し先は写し元ではないので止まる。
        List<Added> earlier = List.copyOf(PENDING);
        PENDING.clear();
        for (Added added : earlier) {
            mirror(added.card(), added.machine(), added.maxSupported(), added.tooltipGroup());
        }
    }

    private static void mirror(Item card, Item machine, int maxSupported, @Nullable String tooltipGroup) {
        List<Item> targets = mirrors.get(machine);
        if (targets == null) {
            return;
        }
        for (Item target : targets) {
            // こちらで明示的に登録したもの・既に写したものは上書きしない。
            if (Upgrades.getMaxInstallable(card, target) == 0) {
                // ツールチップのまとめ先も写し元と同じにする (「ME インターフェイス」の行に一緒に並ぶ)。
                Upgrades.add(card, target, maxSupported, tooltipGroup);
            }
        }
    }
}
