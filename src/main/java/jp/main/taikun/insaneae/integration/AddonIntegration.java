package jp.main.taikun.insaneae.integration;

import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.util.List;

/**
 * 他の AE2 アドオンとの連携 1 つぶん。対象 Mod が入っているときだけ実体が作られる。
 *
 * <p>本体側 (クリエイティブタブ・アップグレード登録・capability・色付け・レシピ生成) は
 * {@link AddonIntegrations#active()} を回してここを呼ぶだけで、<b>連携先ごとの分岐を持たない</b>。
 * 新しいアドオンに対応するときは、このインターフェイスを実装して
 * {@link AddonIntegrations} の名簿に 1 行足す。</p>
 *
 * <p>実装クラスは相手 Mod のクラスを直接参照してよい (compileOnly で型を見る)。
 * 相手が居ないときはクラスごとロードされないよう、名簿側がラムダ越しに生成している。
 * どのメソッドも既定は「何もしない」なので、要るものだけ上書きすればよい。</p>
 */
public interface AddonIntegration {

    /** 相手の modid。レシピの {@code mod_loaded} 条件にも使う。 */
    String modId();

    /** Mod 構築時。アイテムを登録するならここで {@code DeferredRegister} に足す。 */
    default void registerContent() {
    }

    /** 追加した素材 (コンポーネントなど)。クリエイティブタブでセルより前に並ぶ。 */
    default List<? extends ItemLike> materials() {
        return List.of();
    }

    /**
     * 追加した通常セル。AE2 の {@code BasicStorageCell} と同じ色付け
     * (layer1 = 中身の量の LED) が掛かり、クリエイティブタブにも並ぶ。
     */
    default List<? extends ItemLike> storageCells() {
        return List.of();
    }

    /**
     * 追加したポータブルセル。AE2 の {@code AbstractPortableCell} と同じ色付け
     * (layer2 = 画面) が掛かり、クリエイティブタブにも並ぶ。
     */
    default List<? extends ItemLike> portableCells() {
        return List.of();
    }

    /** commonSetup。アップグレードカードの対応付け ({@code Upgrades.add}) をここで。 */
    default void registerUpgrades() {
    }

    /**
     * capability の登録。NeoForge では各 Mod が自分の型にしか登録しないので、
     * 相手の仕組みに乗せた自前のアイテムはここで登録し直す。
     */
    default void registerCapabilities(RegisterCapabilitiesEvent event) {
    }

    /**
     * datagen。階層ごとに呼ばれる。{@code output} は既に {@code mod_loaded} 条件付きなので、
     * 相手が居ないワールドではここで出したレシピは無効になる。
     *
     * @param component その階層のセルコンポーネント
     */
    default void buildRecipes(RecipeOutput output, InsaneCraftingUnitType tier, ItemLike component) {
    }
}
