package jp.main.taikun.insaneae.quantum;

import appeng.api.networking.crafting.ICraftingProvider;

/**
 * 「同じパターンをN回まとめて処理できる」クラフトプロバイダ。
 *
 * <p>AE2の {@link ICraftingProvider#pushPattern} は1クラフト = 1呼び出しで、
 * 呼ぶ側 ({@code CraftingCpuLogic.executeCrafting}) が1回ごとに
 * {@code KeyCounter} の生成・材料の取り出し・電力の消費を行う。
 * つまりクラフト数に比例した固定コストがAE2側に発生し、
 * 100万クラフト/tickのような規模ではそこが支配的になる。</p>
 *
 * <p>そこで {@code CraftingCpuLogicBulkMixin} が、まとめて処理できるプロバイダに対してだけ
 * 「材料をN回ぶんまとめて取り出して1回で渡す」経路を用意する。
 * 実装していない普通のプロバイダには一切影響しない。</p>
 *
 * <p>プロバイダのクラスにこのインターフェイスを実装できない場合。InsaneAEを任意依存にしている
 * 他Modなど) は、{@link BulkCraftingProviderAdapters} にアダプタを登録すれば同じ経路に乗る。
 * 契約はどちらも {@link BulkCraftingHandler} に書いてある。</p>
 */
public interface IBulkCraftingProvider extends ICraftingProvider, BulkCraftingHandler {
}
