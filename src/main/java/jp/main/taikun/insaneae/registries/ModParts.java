package jp.main.taikun.insaneae.registries;

import appeng.api.parts.PartModels;
import appeng.api.util.AEColor;
import appeng.items.parts.ColoredPartItem;
import appeng.items.parts.PartItem;
import jp.main.taikun.insaneae.InsaneAE;
import jp.main.taikun.insaneae.iface.InsaneInterfacePart;
import jp.main.taikun.insaneae.iface.InsaneInterfacePartItem;
import jp.main.taikun.insaneae.network.CompressedCablePart;
import jp.main.taikun.insaneae.network.HyperCablePart;
import jp.main.taikun.insaneae.provider.InsanePatternProviderPart;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
//? if >=1.21 {
import net.neoforged.neoforge.registries.DeferredItem;
//?}
//? if <1.21 {
/*import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.ForgeRegistries;
import net.neoforged.neoforge.registries.RegistryObject;
*///?}

/**
 * ケーブルに貼れる版、つまりプレート。
 *
 * <p>AE2の「MEインターフェイス」と「MEパターンプロバイダ」にブロック版とケーブル版が
 * あるのと同じで、こちらの超特大インターフェイス / 特大パターンプロバイダーにも
 * ケーブル版を用意する。中身はブロック版とまったく同じロジックで、
 * 器がブロックか部品かだけが違う
 * 部品側は{@link InsaneInterfacePart}と{@link InsanePatternProviderPart}。</p>
 *
 * <h2>部品は「ブロック」ではなく「アイテム」として登録する</h2>
 * <p>AE2の部品はワールド上ではケーブルバス ({@code ae2:cable_bus}) の一部で、
 * 自分のブロックもBlockEntityも持たない。したがって
 * 登録するのはアイテムだけで、{@link PartItem} が
 * 「どのクラスの部品を、どう作るか」を持つ。AE2は
 * {@code BuiltInRegistries.ITEM} から直接引く ({@code IPartItem.byId}) ので、
 * 部品用の追加レジストリに名乗りを上げる必要は無い。</p>
 *
 * <h2>モデルだけは自分で申告する</h2>
 * <p>部品のモデルはブロックステートからは辿れないので、焼く対象を
 * {@link PartModels#registerModels} で先に渡しておかないと
 * 読み込まれず紫黒のブロックになる。AE2は自分の部品クラスに付いた
 * {@code @PartModels} 注釈を走査しているが、ここでは注釈に頼らず
 * 各部品クラスの{@code models()}をそのまま渡す。走査より読みやすく、
 * 借りているインジケータも取りこぼさない)。</p>
 *
 * <p>{@link PartModels} は途中で凍結されるので、登録はModのコンストラクタ中
 * (= {@link #register}) に済ませること。</p>
 */
public final class ModParts {

    //? if >=1.21 {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(InsaneAE.MODID);

    public static final DeferredItem<InsaneInterfacePartItem> INSANE_INTERFACE =
            ITEMS.registerItem("insane_interface_part", InsaneInterfacePartItem::new);

    public static final DeferredItem<PartItem<InsanePatternProviderPart>> INSANE_PATTERN_PROVIDER =
            ITEMS.registerItem("insane_pattern_provider_part",
                    props -> new PartItem<>(props, InsanePatternProviderPart.class,
                            InsanePatternProviderPart::new));

    /**
     * 超次元MEケーブル。AE2のケーブルと同じく16色 + fluixの17種類を出す。
     *
     * <p>登録名はAE2に合わせて{@code <色>_hyper_cable}、たとえば{@code fluix_hyper_cable}とする。
     * 色は {@code AEColor#registryPrefix} をそのまま使うので、AE2のケーブルと
     * 並び順も名前の付き方も揃う。</p>
     *
     * <p>色を変える経路は2つあり、どちらもアイテムを差し替える方式:
     * 色塗り器 / ペイントボールは {@link HyperCablePart#changeColor}、
     * クラフトは染料レシピ ({@code ModRecipeProvider})。したがって
     * ここの17種が揃っていないと色替えが失敗する。</p>
     *
     * <p>ケーブルは他の部品と違って {@link PartModels} への申告が要らない。
     * ワールド上の描画は部品のモデルではなくAE2の {@code CableBuilder} が
     * {@code AECableType} と {@code AEColor} から直接組み立てるため。</p>
     */
    public static final Map<AEColor, DeferredItem<ColoredPartItem<HyperCablePart>>> HYPER_CABLES =
    //?} else {
    /*private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, InsaneAE.MODID);

    public static final RegistryObject<InsaneInterfacePartItem> INSANE_INTERFACE =
            ITEMS.register("insane_interface_part",
                    () -> new InsaneInterfacePartItem(new Item.Properties()));

    public static final RegistryObject<PartItem<InsanePatternProviderPart>> INSANE_PATTERN_PROVIDER =
            ITEMS.register("insane_pattern_provider_part",
                    () -> new PartItem<>(new Item.Properties(),
                            InsanePatternProviderPart.class, InsanePatternProviderPart::new));

    /^*
     * 超次元MEケーブル。AE2のケーブルと同じく16色 + fluixの17種類を出す。
     *
     * <p>登録名はAE2に合わせて{@code <色>_hyper_cable}、たとえば{@code fluix_hyper_cable}とする。
     * 色は {@code AEColor#registryPrefix} をそのまま使うので、AE2のケーブルと
     * 並び順も名前の付き方も揃う。</p>
     *
     * <p>色を変える経路は2つあり、どちらもアイテムを差し替える方式:
     * 色塗り器 / ペイントボールは {@link HyperCablePart#changeColor}、
     * クラフトは染料レシピ ({@code ModRecipeProvider})。したがって
     * ここの17種が揃っていないと色替えが失敗する。</p>
     *
     * <p>ケーブルは他の部品と違って {@link PartModels} への申告が要らない。
     * ワールド上の描画は部品のモデルではなくAE2の {@code CableBuilder} が
     * {@code AECableType} と {@code AEColor} から直接組み立てるため。</p>
     ^/
    public static final Map<AEColor, RegistryObject<ColoredPartItem<HyperCablePart>>> HYPER_CABLES =
    *///?}
            new EnumMap<>(AEColor.class);

    /**
     * 圧縮ME高密度スマートケーブル。超次元MEケーブルと同じく17色を出す。
     *
     * <p>登録名は {@code <色>_compressed_dense_cable}。本数は高密度ケーブルと同じ32本で、
     * 違うのは細くて部品が貼れること ({@link CompressedCablePart})。</p>
     */
    //? if >=1.21 {
    public static final Map<AEColor, DeferredItem<ColoredPartItem<CompressedCablePart>>> COMPRESSED_CABLES =
    //?} else {
    /*public static final Map<AEColor, RegistryObject<ColoredPartItem<CompressedCablePart>>> COMPRESSED_CABLES =
    *///?}
            new EnumMap<>(AEColor.class);

    static {
        for (AEColor color : AEColor.values()) {
            //? if >=1.21 {
            HYPER_CABLES.put(color, ITEMS.registerItem(color.registryPrefix + "_hyper_cable",
                    props -> new ColoredPartItem<>(props, HyperCablePart.class, HyperCablePart::new,
                            color)));
            COMPRESSED_CABLES.put(color, ITEMS.registerItem(
            //?} else {
            /*HYPER_CABLES.put(color, ITEMS.register(color.registryPrefix + "_hyper_cable",
                    () -> new ColoredPartItem<>(new Item.Properties(),
                            HyperCablePart.class, HyperCablePart::new, color)));
            COMPRESSED_CABLES.put(color, ITEMS.register(
            *///?}
                    color.registryPrefix + "_compressed_dense_cable",
                    //? if >=1.21 {
                    props -> new ColoredPartItem<>(props, CompressedCablePart.class,
                            CompressedCablePart::new, color)));
                    //?} else {
                    /*() -> new ColoredPartItem<>(new Item.Properties(),
                            CompressedCablePart.class, CompressedCablePart::new, color)));
                    *///?}
        }
    }

    /** その色の超次元MEケーブルのアイテム。全色そろえてあるのでnullにはならない。 */
    public static ColoredPartItem<HyperCablePart> hyperCable(AEColor color) {
        return HYPER_CABLES.get(color).get();
    }

    /** 全色の超次元MEケーブル。クリエイティブタブとレシピ生成用で、AE2と同じ並び順。 */
    public static List<ColoredPartItem<HyperCablePart>> allHyperCables() {
        return Arrays.stream(AEColor.values()).map(ModParts::hyperCable).toList();
    }

    /** その色の圧縮ME高密度スマートケーブルのアイテム。全色そろえてあるのでnullにはならない。 */
    public static ColoredPartItem<CompressedCablePart> compressedCable(AEColor color) {
        return COMPRESSED_CABLES.get(color).get();
    }

    /** 全色の圧縮ME高密度スマートケーブル。クリエイティブタブ / レシピ生成用。 */
    public static List<ColoredPartItem<CompressedCablePart>> allCompressedCables() {
        return Arrays.stream(AEColor.values()).map(ModParts::compressedCable).toList();
    }

    private ModParts() {
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);

        List<ResourceLocation> models = new ArrayList<>();
        models.addAll(InsaneInterfacePart.models());
        models.addAll(InsanePatternProviderPart.models());
        PartModels.registerModels(models);
    }
}
