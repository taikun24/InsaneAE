package jp.main.taikun.insaneae.integration;

import jp.main.taikun.insaneae.crafting.InsaneCraftingUnitType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.List;
import java.util.Map;
import java.util.function.ToIntBiFunction;
//? if >=1.21 {
import net.minecraft.data.recipes.RecipeOutput;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
//?}
//? if <1.21 {
/*import net.minecraft.core.Direction;
import net.minecraft.data.recipes.FinishedRecipe;
import java.util.function.Consumer;
*///?}

/**
 * 他のAE2アドオンとの連携1つぶん。対象Modが入っているときだけ実体が作られる。
 *
 * <p>本体側、つまりクリエイティブタブ、アップグレード登録、capability、色付け、レシピ生成は
 * {@link AddonIntegrations#active()} を回してここを呼ぶだけで、連携先ごとの分岐を持たない。
 * 新しいアドオンに対応するときは、このインターフェイスを実装して
 * {@link AddonIntegrations} の名簿に1行足す。</p>
 *
 * <p>実装クラスは相手Modのクラスを直接参照してよい。compileOnlyで型を見る。
 * 相手が居ないときはクラスごとロードされないよう、名簿側がラムダ越しに生成している。
 * どのメソッドも既定は「何もしない」なので、要るものだけ上書きすればよい。</p>
 */
public interface AddonIntegration {

    /** 相手のmodid。レシピの {@code mod_loaded} 条件にも使う。 */
    String modId();

    /**
     * 連携が働くのに要るMod全部。名簿はこれが全部入っているときだけ実体を作り、
     * レシピにも全部の {@code mod_loaded} 条件を付ける。
     */
    default List<String> requiredMods() {
        return List.of(modId());
    }

    /** Mod構築時。アイテムを登録するならここで {@code DeferredRegister} に足す。 */
    default void registerContent() {
    }

    /** 追加した素材、たとえばコンポーネント。クリエイティブタブでセルより前に並ぶ。 */
    default List<? extends ItemLike> materials() {
        return List.of();
    }

    /**
     * 追加した通常セル。AE2の {@code BasicStorageCell} と同じ色付け
     * layer1は中身の量のLEDで、それが掛かる。クリエイティブタブにも並ぶ。
     */
    default List<? extends ItemLike> storageCells() {
        return List.of();
    }

    /**
     * 追加したポータブルセル。AE2の {@code AbstractPortableCell} と同じ色付け
     * layer2は画面で、それが掛かる。クリエイティブタブにも並ぶ。
     */
    default List<? extends ItemLike> portableCells() {
        return List.of();
    }

    /**
     * 色ハンドラを相手のModのものにするセル、たとえばAE2 ThingsのDISKなど。値は
     * {@code ItemColor}と同じで、スタックとtintindexからARGBを返す。クリエイティブタブにも並ぶ。
     */
    default Map<Item, ToIntBiFunction<ItemStack, Integer>> cellsWithOwnColor() {
        return Map.of();
    }

    /** commonSetup。アップグレードカードの対応付け ({@code Upgrades.add}) をここで。 */
    default void registerUpgrades() {
    }

    /**
     * capabilityの登録。NeoForgeでは各Modが自分の型にしか登録しないので、
     * 相手の仕組みに乗せた自前のアイテムはここで登録し直す。
     */
    //? if >=1.21 {
    default void registerCapabilities(RegisterCapabilitiesEvent event) {
    //?} else {
    /*default void onInsaneHostNeighborChanged(Object logic, Direction side) {
    *///?}
    }

    /**
     * datagen。階層ごとに呼ばれる。{@code output} は既に {@code mod_loaded} 条件付きなので、
     * 相手が居ないワールドではここで出したレシピは無効になる。
     *
     * @param componentその階層のセルコンポーネント
     */
    //? if >=1.21 {
    default void buildRecipes(RecipeOutput output, InsaneCraftingUnitType tier, ItemLike component) {
    //?} else {
    /*default void buildRecipes(Consumer<FinishedRecipe> output, InsaneCraftingUnitType tier, ItemLike component) {
    *///?}
    }
}
