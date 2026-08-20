package jp.main.taikun.insaneae.registries;

import appeng.api.parts.PartModels;
import appeng.items.parts.PartItem;
import jp.main.taikun.insaneae.InsaneAE;
import jp.main.taikun.insaneae.iface.InsaneInterfacePart;
import jp.main.taikun.insaneae.provider.InsanePatternProviderPart;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

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

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, InsaneAE.MODID);

    /** 超特大インターフェイスのケーブル版。 */
    public static final RegistryObject<PartItem<InsaneInterfacePart>> INSANE_INTERFACE =
            ITEMS.register("insane_interface_part",
                    () -> new PartItem<>(new Item.Properties(),
                            InsaneInterfacePart.class, InsaneInterfacePart::new));

    /** 特大パターンプロバイダーのケーブル版。 */
    public static final RegistryObject<PartItem<InsanePatternProviderPart>> INSANE_PATTERN_PROVIDER =
            ITEMS.register("insane_pattern_provider_part",
                    () -> new PartItem<>(new Item.Properties(),
                            InsanePatternProviderPart.class, InsanePatternProviderPart::new));

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
