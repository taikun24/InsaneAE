#!/usr/bin/env python3
"""超特大インターフェイスのブロックテクスチャを生成する。

    pip install pillow
    python tools/gen_interface_texture.py

出力は 1 枚だけ。

    src/main/resources/assets/insaneae/textures/block/insane_interface.png

下敷きは tools/textures/[<mc version>/]interface_base.png (= AE2 の ME インターフェイスの
テクスチャそのもの)。<b>彩度のある画素 (水色のパネル) の色相だけを回す</b>ので、
白い枠や暗い縁はそのまま残り、AE2 のインターフェイスの「続き」に見える。
色は INTERFACE_COLOR を書き換えるだけで変わる。

色相を回すだけだと色によって明るさが桁違いに変わるので、keep_luma=True で
回す前の相対輝度に合わせ直している (詳細は gen_crafting_textures.hue_rotate)。
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

TEMPLATE = "interface_base.png"

# 限界突破ブロックらしい紫。AE2 のインターフェイスは水色 (色相 0.58) なので、
# 並べたときに一目で別物と分かる位置に置いてある。
INTERFACE_COLOR = (0xC0, 0x5C, 0xFF)


def main() -> None:
    base = load_template(TEMPLATE_DIRS, TEMPLATE)
    if base is None:
        raise SystemExit(
            "下敷きが見つからない: {} を {} のいずれかに置くこと"
            " (AE2 の assets/ae2/textures/block/interface.png をコピーすればよい)"
            .format(TEMPLATE, " / ".join(TEMPLATE_DIRS)))

    out = hue_rotate(base, INTERFACE_COLOR, keep_luma=True)

    os.makedirs(OUT_DIR, exist_ok=True)
    path = os.path.join(OUT_DIR, "insane_interface.png")
    out.save(path)
    print("wrote", os.path.relpath(path, REPO))


if __name__ == "__main__":
    main()
