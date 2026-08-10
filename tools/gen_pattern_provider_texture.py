#!/usr/bin/env python3
"""特大パターンプロバイダーのブロックテクスチャを生成する。

    pip install pillow
    python tools/gen_pattern_provider_texture.py

出力は 1 枚だけ。

    src/main/resources/assets/insaneae/textures/block/insane_pattern_provider.png

下敷きは tools/textures/[<mc version>/]pattern_provider_base.png (= AE2 のパターンプロバイダの
テクスチャそのもの)。仕組みは gen_interface_texture.py と同じで、彩度のある画素の色相だけを
限界突破ブロック色 (紫) に回す。keep_luma で元の明るさを保つので模様はそのまま残る。
"""

from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from gen_crafting_textures import (  # noqa: E402  (パスを通してから読む)
    REPO,
    TEMPLATE_DIRS,
    hue_rotate,
    load_template,
)

OUT_DIR = os.path.join(REPO, "src/main/resources/assets/insaneae/textures/block")

TEMPLATE = "pattern_provider_base.png"

# 超特大インターフェイスと同じ「限界突破ブロックの紫」。
PROVIDER_COLOR = (0xC0, 0x5C, 0xFF)


def main() -> None:
    base = load_template(TEMPLATE_DIRS, TEMPLATE)
    if base is None:
        raise SystemExit(
            "下敷きが見つからない: {} を {} のいずれかに置くこと"
            " (AE2 の assets/ae2/textures/block/pattern_provider.png をコピーすればよい)"
            .format(TEMPLATE, " / ".join(TEMPLATE_DIRS)))

    out = hue_rotate(base, PROVIDER_COLOR, keep_luma=True)

    os.makedirs(OUT_DIR, exist_ok=True)
    path = os.path.join(OUT_DIR, "insane_pattern_provider.png")
    out.save(path)
    print("wrote", os.path.relpath(path, REPO))


if __name__ == "__main__":
    main()
