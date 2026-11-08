package jp.main.taikun.insaneae.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 説明文をツールチップの行に足す。
 *
 * <p>langの値は1行ごとに {@codeラベル|値} と書く。値の側だけ色を変えて、
 * 数値が目に入るようにする。{@codeパターン格納数|1620個}は「パターン格納数1620個」になる。
 * {@code |} の無い行は注記としてそのまま灰色で出す。改行はlangの {@code \n}。</p>
 */
public final class StatTooltip {
    private static final ChatFormatting LABEL = ChatFormatting.GRAY;
    private static final ChatFormatting VALUE = ChatFormatting.AQUA;

    private StatTooltip() {
    }

    public static void add(List<Component> lines, Component text) {
        // Tooltipは1要素 = 1行なので、翻訳後の文字列を行ごとに分ける
        for (String line : text.getString().split("\\R")) {
            int bar = line.indexOf('|');
            if (bar < 0) {
                lines.add(Component.literal(line).withStyle(LABEL));
                continue;
            }
            lines.add(Component.literal(line.substring(0, bar).strip() + " ").withStyle(LABEL)
                    .append(Component.literal(line.substring(bar + 1).strip()).withStyle(VALUE)));
        }
    }
}
