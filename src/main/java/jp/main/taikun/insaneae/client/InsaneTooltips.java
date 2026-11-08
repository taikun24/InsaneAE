package jp.main.taikun.insaneae.client;

import jp.main.taikun.insaneae.energy.SolarPanelTier;
import jp.main.taikun.insaneae.integration.aco.AcoBigIntegerLimitBridge;
import jp.main.taikun.insaneae.registries.ModBlocks;
import jp.main.taikun.insaneae.registries.ModParts;
import jp.main.taikun.insaneae.util.StatTooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
//? if >=1.21 {
import jp.main.taikun.insaneae.registries.ModDataComponents;
//?}
//? if <1.21 {
/*import jp.main.taikun.insaneae.cell.storage.CellNbt;
*///?}

/**
 * アイテムの説明文、つまりロアをツールチップに足す。
 *
 * <p>文章はすべてlangファイルに置く。キーは次の順で探し、最初に見つかったものを出す。</p>
 * <ol>
 *   <li>{@code <そのアイテムの翻訳キー>.desc} — 例: {@code block.insaneae.quantum_cpu.desc}</li>
 *   <li>{@link #families()} の共通キー — 階層違いで文面が同じもの用
 *       今はソーラーパネル4種の{@code insaneae.desc.solar_panel}だけ</li>
 * </ol>
 *
 * <p>共通キーの方は書式引数を渡せるので、階層ごとに違う数値を文中に出せる
 * ソーラーパネルの発電量などに使う。値の書き方は{@link StatTooltip}を参照し、ラベルと値は{@code |}で区切り、改行は{@code \n}で書く。</p>
 *
 * <p>説明には仕様の値だけを書く。理由・使い分け・注意書きは載せない
 * 画面や名前で分かることを毎回読ませないため。ゲーム中に他から読み取れない値
 * — 枠数・上限・速度・チャンネル数 — だけを書くこと。</p>
 *
 * <p>増やしたいときはlangにキーを足すだけでよく、階層共通にしたいときだけ
 * {@link #families()} に1行足す。</p>
 *
 * <p>加速カードのように {@code appendHoverText} で自前の行を出しているアイテムは
 * {@code .tooltip} キーを使っているので、ここの {@code .desc} とは衝突しない。</p>
 */
public final class InsaneTooltips {
    //? if <1.21 {
    /*private static final String CELL_ID = "insaneae.tooltip.cell_id";
    *///?}

    private static final String SUFFIX = ".desc";
    private static final String SOLAR_PANEL = "insaneae.desc.solar_panel";
    /** 超次元MEケーブルは17色あるが説明は共通なので、色ごとの .descは置かない。 */
    private static final String HYPER_CABLE = "insaneae.desc.hyper_cable";
    /** 圧縮ME高密度スマートケーブルも同様。 */
    private static final String COMPRESSED_CABLE = "insaneae.desc.compressed_dense_cable";
    //? if >=1.21 {
    private static final String CELL_ID = "insaneae.tooltip.cell_id";
    //?}
    private static final String BIG_INTEGER_CPU_CAPACITY =
            "block.insaneae.big_integer_cpu.capacity";

    /** 説明のキーと、そこに流し込む書式引数。 */
    private record Description(String key, Object... args) {
    }

    /** アイテム → 階層共通の説明。初回のツールチップ描画時に組む。 */
    private static Map<Item, Description> families;

    private InsaneTooltips() {
    }

    /** クライアント初期化時に1度だけ呼ぶ。 */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(InsaneTooltips::onTooltip);
    }

    private static void onTooltip(ItemTooltipEvent event) {
        Item item = event.getItemStack().getItem();
        // 中身を失ったセルを /insaneae cells recoverで作り直すときに要る。普段は邪魔なので詳細表示のときだけ
        //? if >=1.21 {
        UUID cellId = event.getItemStack().get(ModDataComponents.CELL_ID.get());
        //?} else {
        /*UUID cellId = CellNbt.id(event.getItemStack());
        *///?}
        if (cellId != null && event.getFlags().isAdvanced()) {
            event.getToolTip().add(Component.translatable(CELL_ID, cellId.toString())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        Description description = descriptionFor(item);
        if (description != null) {
            StatTooltip.add(event.getToolTip(), Component.translatable(description.key(), description.args()));
        }

        // BigIntegerクラフトストレージだけは、ACO側の計算・保存上限をカタログ容量として表示する
        if (item != ModBlocks.BIG_INTEGER_CPU.get().asItem()) {
            return;
        }
        AcoBigIntegerLimitBridge.theoreticalMaximumDisplay().ifPresent(capacity ->
                StatTooltip.add(event.getToolTip(), Component.translatable(BIG_INTEGER_CPU_CAPACITY, capacity)));
    }

    /** そのアイテムに出す説明。個別、階層共通の順で探す。無ければnull。 */
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
                // 階層ごとに違う発電量を文中に出す。%sは快晴時の最大AE/t
                // 最上段は42億なので3桁区切りにしておく。区切り文字は環境非依存にしたいのでROOT
                map.put(ModBlocks.SOLAR_PANELS.get(tier).get().asItem(),
                        new Description(SOLAR_PANEL,
                                String.format(Locale.ROOT, "%,d", tier.ratePerTick())));
            }
            for (var cable : ModParts.allHyperCables()) {
                map.put(cable, new Description(HYPER_CABLE));
            }
            for (var cable : ModParts.allCompressedCables()) {
                map.put(cable, new Description(COMPRESSED_CABLE));
            }
            families = map;
        }
        return families;
    }
}
