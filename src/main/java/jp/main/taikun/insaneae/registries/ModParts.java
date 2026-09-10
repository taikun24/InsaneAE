package jp.main.taikun.insaneae.registries;

import appeng.api.parts.PartModels;
import appeng.api.util.AEColor;
import appeng.items.parts.ColoredPartItem;
import appeng.items.parts.PartItem;
import jp.main.taikun.insaneae.InsaneAE;
import jp.main.taikun.insaneae.iface.InsaneInterfacePart;
import jp.main.taikun.insaneae.iface.InsaneInterfacePartItem;
import jp.main.taikun.insaneae.network.HyperCablePart;
import jp.main.taikun.insaneae.provider.InsanePatternProviderPart;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * ケーブルに貼れる版 (プレート)。
 *
 * <p>AE2 の「ME インターフェイス」と「ME パターンプロバイダ」にブロック版とケーブル版が
 * あるのと同じで、こちらの超特大インターフェイス / 特大パターンプロバイダーにも
 * ケーブル版を用意する。中身はブロック版とまったく同じロジックで、
 * 器がブロックか部品かだけが違う → {@link InsaneInterfacePart} /
 * {@link InsanePatternProviderPart}。</p>
 *
 * <h2>部品は「ブロック」ではなく「アイテム」として登録する</h2>
 * <p>AE2 の部品はワールド上ではケーブルバス ({@code ae2:cable_bus}) の一部で、
 * 自分のブロックも BlockEntity も持たない。したがって
 * <b>登録するのはアイテムだけ</b>で、{@link PartItem} が
 * 「どのクラスの部品を、どう作るか」を持つ。AE2 は
 * {@code BuiltInRegistries.ITEM} から直接引く ({@code IPartItem.byId}) ので、
 * 部品用の追加レジストリに名乗りを上げる必要は無い。</p>
 *
 * <h2>モデルだけは自分で申告する</h2>
 * <p>部品のモデルはブロックステートからは辿れないので、焼く対象を
 * {@link PartModels#registerModels} で先に渡しておかないと
 * <b>読み込まれず紫黒のブロックになる</b>。AE2 は自分の部品クラスに付いた
 * {@code @PartModels} 注釈を走査しているが、ここでは注釈に頼らず
 * 各部品クラスの {@code models()} をそのまま渡す (走査より読みやすく、
 * 借りているインジケータも取りこぼさない)。</p>
 *
 * <p>{@link PartModels} は途中で凍結されるので、登録は<b>Mod のコンストラクタ中</b>
 * (= {@link #register}) に済ませること。</p>
 */
public final class ModParts {

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(InsaneAE.MODID);

    /** 超特大インターフェイスのケーブル版。 */
    public static final DeferredItem<InsaneInterfacePartItem> INSANE_INTERFACE =
            ITEMS.registerItem("insane_interface_part", InsaneInterfacePartItem::new);

    /** 特大パターンプロバイダーのケーブル版。 */
    public static final DeferredItem<PartItem<InsanePatternProviderPart>> INSANE_PATTERN_PROVIDER =
            ITEMS.registerItem("insane_pattern_provider_part",
                    props -> new PartItem<>(props, InsanePatternProviderPart.class,
                            InsanePatternProviderPart::new));

    /**
     * 超次元 ME ケーブル。AE2 のケーブルと同じく<b>16 色 + fluix の 17 種類</b>を出す。
     *
     * <p>登録名は AE2 に合わせて {@code <色>_hyper_cable} ({@code fluix_hyper_cable} ほか)。
     * 色は {@code AEColor#registryPrefix} をそのまま使うので、AE2 のケーブルと
     * <b>並び順も名前の付き方も揃う</b>。</p>
     *
     * <p>色を変える経路は 2 つあり、どちらも<b>アイテムを差し替える</b>方式:
     * 色塗り器 / ペイントボールは {@link HyperCablePart#changeColor}、
     * クラフトは染料レシピ ({@code ModRecipeProvider})。したがって
     * <b>ここの 17 種が揃っていないと色替えが黙って失敗する</b>。</p>
     *
     * <p>ケーブルは他の部品と違って {@link PartModels} への申告が要らない。
     * ワールド上の描画は部品のモデルではなく AE2 の {@code CableBuilder} が
     * {@code AECableType} と {@code AEColor} から直接組み立てるため。</p>
     */
    public static final Map<AEColor, DeferredItem<ColoredPartItem<HyperCablePart>>> HYPER_CABLES =
            new EnumMap<>(AEColor.class);

    static {
        for (AEColor color : AEColor.values()) {
            HYPER_CABLES.put(color, ITEMS.registerItem(color.registryPrefix + "_hyper_cable",
                    props -> new ColoredPartItem<>(props, HyperCablePart.class, HyperCablePart::new,
                            color)));
        }
    }

    /** その色の超次元 ME ケーブルのアイテム。全色そろえてあるので null にはならない。 */
    public static ColoredPartItem<HyperCablePart> hyperCable(AEColor color) {
        return HYPER_CABLES.get(color).get();
    }

    /** 全色の超次元 ME ケーブル (クリエイティブタブ / レシピ生成用)。AE2 と同じ並び順。 */
    public static List<ColoredPartItem<HyperCablePart>> allHyperCables() {
        return Arrays.stream(AEColor.values()).map(ModParts::hyperCable).toList();
    }

    private ModParts() {
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);

        List<net.minecraft.resources.ResourceLocation> models = new ArrayList<>();
        models.addAll(InsaneInterfacePart.models());
        models.addAll(InsanePatternProviderPart.models());
        PartModels.registerModels(models);
    }
}
