#!/usr/bin/env python3
"""Insane プロセッサ一式 (金型 / 回路 / プロセッサ) のアイテムテクスチャを生成する。

    python tools/gen_processor_textures.py

下敷きは AE2 の演算プロセッサの 3 枚 (tools/textures/ の processor.png /
printed_processor.png / processor_press.png)。AE2 のプロセッサは基板が共通で、
<b>足 (と回路) の色だけ</b>で種類を見分けている (演算 = 水色、工学 = シアン、論理 = 金)。
Insane プロセッサは、その色の付いた部分を<b>虹色</b>にする
(InsaneAE で「最上段 = 虹」としている 8E セルなどと揃える)。

色を回すのは下敷きの主要色相 (水色) の近くの画素だけ。プロセッサの赤い点など、
別の色相の画素はそのまま残す。金型は無彩色なので、彫りの部分に薄く虹を乗せる。
"""

from __future__ import annotations

import colorsys
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from gen_crafting_textures import (  # noqa: E402
    MIN_SAT,
    dominant_hue,
    load_template,
)

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(REPO, "src/main/resources/assets/insaneae/textures/item")
# main (1.20.1) の gen_crafting_textures にはバージョン別ディレクトリの解決が無いので、
# tools/textures/ だけを見る (下敷きは AE2 15.x の絵)。
TEMPLATE_DIRS = os.path.join(REPO, "tools/textures")

# 下敷きの主要色相からこれ以内の画素だけ虹にする (色相は 0〜1)。
HUE_WINDOW = 0.12
# これより暗い画素は虹にしない。プロセッサの基板 (暗い青灰色) も彩度がわずかにあるので、
# 色相だけで選ぶと基板ごと虹色になってしまう。
MIN_VALUE = 0.5
# 虹の向きと刻み。x+y が 1 増えるごとに回す色相。
RAINBOW_STEP = 1.0 / 14.0
# 虹の起点の色相 (マゼンタ寄り。8E と同じ系統)。
RAINBOW_START = 0.83
# 金型の彫りに乗せる虹の濃さ。
PRESS_TINT = 0.45


def rainbow_hue(x: int, y: int) -> float:
    return (RAINBOW_START + RAINBOW_STEP * (x + y)) % 1.0


def rainbowize(template: Image.Image) -> Image.Image:
    """主要色相の近くの画素を、明度と彩度を保ったまま虹色に回す。"""
    src = template.convert("RGBA")
    ref_h, _, _ = dominant_hue(src, MIN_SAT)
    out = src.copy()
    px = out.load()
    for y in range(src.height):
        for x in range(src.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            distance = min(abs(h - ref_h), 1 - abs(h - ref_h))
            if s < MIN_SAT or v < MIN_VALUE or distance > HUE_WINDOW:
                continue
            # 水色は彩度が低めなので、虹がくすまないよう少し持ち上げる。
            nr, ng, nb = colorsys.hsv_to_rgb(rainbow_hue(x, y), min(1.0, s * 1.6), v)
            px[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
    return out


def tint_press(template: Image.Image) -> Image.Image:
    """無彩色の金型に虹を薄く乗せる。暗い画素 (彫り) ほど強く乗せる。"""
    src = template.convert("RGBA")
    out = src.copy()
    px = out.load()
    for y in range(src.height):
        for x in range(src.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            v = max(r, g, b) / 255
            k = PRESS_TINT * (1.0 - v * 0.6)
            tr, tg, tb = colorsys.hsv_to_rgb(rainbow_hue(x, y), 0.8, v)
            px[x, y] = (round(r * (1 - k) + tr * 255 * k),
                        round(g * (1 - k) + tg * 255 * k),
                        round(b * (1 - k) + tb * 255 * k), a)
    return out


def main() -> None:
    outputs = {
        "insane_processor.png": ("processor.png", rainbowize),
        "printed_insane_processor.png": ("printed_processor.png", rainbowize),
        "insane_processor_press.png": ("processor_press.png", tint_press),
    }
    for name, (template_name, make) in outputs.items():
        template = load_template(TEMPLATE_DIRS, template_name)
        if template is None:
            sys.exit(f"tools/textures/{template_name} が無い (AE2 の演算プロセッサの絵を置くこと)")
        make(template).save(os.path.join(OUT_DIR, name))
        print(f"{template_name} -> {name}")


if __name__ == "__main__":
    main()
