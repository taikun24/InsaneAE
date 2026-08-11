package jp.main.taikun.insaneae.client;

import jp.main.taikun.insaneae.energy.SolarPanelTier;
import jp.main.taikun.insaneae.integration.aco.AcoBigIntegerLimitBridge;
import jp.main.taikun.insaneae.registries.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * アイテムの説明文 (ロア) をツールチップに足す。
 *
 * <p>文章は<b>すべて lang ファイルに置く</b>。キーは次の順で探し、最初に見つかったものを出す。</p>
 * <ol>
 *   <li>{@code <そのアイテムの翻訳キー>.desc} — 例: {@code block.insaneae.quantum_cpu.desc}</li>
 *   <li>{@link #families()} の共通キー — 階層違いで文面が同じもの用
 *       (今はソーラーパネル 4 種の {@code insaneae.desc.solar_panel} だけ)</li>
 * </ol>
 *
 * <p>共通キーの方は<b>書式引数を渡せる</b>ので、階層ごとに違う数値を文中に出せる
 * (ソーラーパネルの発電量など)。改行は lang の値に {@code \n} を入れる。</p>
 *
 * <p>説明を出しているのは Quantum CPU / Improved Crystal Charger / ソーラーパネルの 3 種だけ。
 * <b>増やしたいときは lang にキーを足すだけ</b>でよく、階層共通にしたいときだけ
 * {@link #families()} に 1 行足す。</p>
 *
 * <p>加速カードのように {@code appendHoverText} で自前の行を出しているアイテムは
 * {@code .tooltip} キーを使っているので、ここの {@code .desc} とは衝突しない。</p>
 */
public final class InsaneTooltips {

    private static final String SUFFIX = ".desc";
    private static final String SOLAR_PANEL = "insaneae.desc.solar_panel";
    private static final String BIG_INTEGER_CPU_CAPACITY =
            "block.insaneae.big_integer_cpu.capacity";

    /** 説明のキーと、そこに流し込む書式引数。 */
    private record Description(String key, Object... args) {
    }

    /** アイテム → 階層共通の説明。初回のツールチップ描画時に組む。 */
    private static Map<Item, Description> families;

    private InsaneTooltips() {
    }

    /** クライアント初期化時に 1 度だけ呼ぶ。 */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(InsaneTooltips::onTooltip);
    }

    private static void onTooltip(ItemTooltipEvent event) {
        Item item = event.getItemStack().getItem();
        Description description = descriptionFor(item);
        // 説明キーがあるアイテムだけ、翻訳済みの複数行説明を追加する。
        if (description != null) {
            String text = Component.translatable(description.key(), description.args()).getString();
            // lang内の改行を、Minecraftの独立したTooltip行へ変換する。
            for (String line : text.split("\\R")) {
                event.getToolTip().add(Component.literal(line).withStyle(ChatFormatting.GRAY));
            }
        }

        // BigIntegerクラフトストレージだけは、ACO側の計算・保存上限をカタログ容量として表示する。
        if (item != ModBlocks.BIG_INTEGER_CPU.get().asItem()) {
            return;
        }
        AcoBigIntegerLimitBridge.theoreticalMaximumDisplay().ifPresent(capacity ->
                event.getToolTip().add(Component.translatable(
                        BIG_INTEGER_CPU_CAPACITY, capacity).withStyle(ChatFormatting.AQUA)));
    }

    /** そのアイテムに出す説明。個別 → 階層共通 の順で探す。無ければ null。 */
    private static Description descriptionFor(Item item) {
        String own = item.getDescriptionId() + SUFFIX;
        if (Language.getInstance().has(own)) {
            return new Description(own);
        }
        Description family = families().get(item);
        return family != null && Language.getInstance().has(family.key()) ? family : null;
    }

    private static Map<Item, Description> families() {
        if (families == null) {
            Map<Item, Description> map = new IdentityHashMap<>();
            for (SolarPanelTier tier : SolarPanelTier.values()) {
                // 階層ごとに違う発電量を文中に出す (%s = 快晴時の最大 AE/t)。
                // 最上段は 42 億なので 3 桁区切りにしておく (区切り文字は環境非依存にしたいので ROOT)。
                map.put(ModBlocks.SOLAR_PANELS.get(tier).get().asItem(),
                        new Description(SOLAR_PANEL,
                                String.format(Locale.ROOT, "%,d", tier.ratePerTick())));
            }
            families = map;
        }
        return families;
    }
}
