package jp.main.taikun.insaneae.quantum;

import appeng.api.networking.crafting.ICraftingProvider;

/**
 * 「同じパターンを N 回まとめて処理できる」クラフトプロバイダ。
 *
 * <p>AE2 の {@link ICraftingProvider#pushPattern} は<b>1 クラフト = 1 呼び出し</b>で、
 * 呼ぶ側 ({@code CraftingCpuLogic.executeCrafting}) が 1 回ごとに
 * {@code KeyCounter} の生成・材料の取り出し・電力の消費を行う。
 * つまりクラフト数に比例した固定コストが AE2 側に発生し、
 * 100 万クラフト/tick のような規模ではそこが支配的になる。</p>
 *
 * <p>そこで {@code CraftingCpuLogicBulkMixin} が、まとめて処理できるプロバイダに対してだけ
 * 「材料を N 回ぶんまとめて取り出して 1 回で渡す」経路を用意する。
 * 実装していない普通のプロバイダには一切影響しない。</p>
 *
 * <p>プロバイダのクラスにこのインターフェイスを実装できない場合 (InsaneAE を任意依存にしている
 * 他 Mod など) は、{@link BulkCraftingProviderAdapters} にアダプタを登録すれば同じ経路に乗る。
 * 契約はどちらも {@link BulkCraftingHandler} に書いてある。</p>
 */
public interface IBulkCraftingProvider extends ICraftingProvider, BulkCraftingHandler {
}
