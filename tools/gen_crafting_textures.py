#!/usr/bin/env python3
"""クラフトストレージ / 協調処理ユニットのブロックテクスチャを「色」から自動生成する。

    pip install pillow

    python tools/gen_crafting_textures.py                  # 生成
    python tools/gen_crafting_textures.py --dump-templates # 下敷きを書き出す

出力先は src/main/resources/assets/insaneae/textures/block/crafting/ で、
階層ごとに次の 3 ファイルを出す。

    <id>_storage.png            16x16   未 formed のブロック面
    <id>_storage_light.png      16xN    formed 時の発光レイヤ (N/16 コマのアニメ)
    <id>_storage_light.png.mcmeta       アニメ定義 (2 コマ以上のときだけ)

色を変えたいときは下の STORAGE_BAND_COLORS / ACCELERATOR_COLOR をいじって流し直すだけ。

--------------------------------------------------------------------------------------
下敷きの置き場所 (MC バージョンごとに分けられる)
--------------------------------------------------------------------------------------
下敷きは次の順に<b>ファイル単位で</b>探す。gradle.properties の minecraft_version を
読むので、チェックアウトしているブランチに合ったものが自動で選ばれる。

    1. tools/textures/<minecraft_version>/   そのバージョン専用の絵
    2. tools/textures/                       バージョン共通の絵

<b>バージョンで変えたい絵だけ</b> tools/textures/<version>/ に置けばよく、
残りは共通のものがそのまま使われる。全部コピーして二重管理する必要はない。
バージョン用のディレクトリが無ければ tools/textures/ だけを見る (従来どおり)。
`--templates <dir>` を渡すとその 1 か所だけを見る。

--------------------------------------------------------------------------------------
ベーステクスチャ / 色テクスチャを差し替える
--------------------------------------------------------------------------------------
上の置き場所に次の名前で置くと、そちらが優先して使われる (無いものは下の
描画コードが手続き的に描く)。`--dump-templates` で今の見た目を書き出せるので、
それをペイントソフトで直してから戻すのが早い (書き出し先は探索パスの先頭)。

    storage_base.png       未 formed 面の下地。階層に依らず<b>そのまま</b>使われる
    storage_color.png      その上に重ねる中央の色レイヤ。階層色に染まる
    storage_light.png      formed の発光レイヤ。階層色に染まる
    accelerator_base.png / accelerator_color.png / accelerator_light.png

<b>base は染めないので、地に色が付いていても構わない</b> (無彩色で描く必要はない)。
階層に依らない部分はすべて base に描くこと。

染める 2 枚 (color / light) は、次のどちらの描き方でもよい。

    グレースケールで描く  明度だけが残り、色相と彩度は階層色のものになる。
                          陰影だけ描けばよいが、1 枚の中で色相は変えられない。
    色を付けて描く        「基準色相 → 階層色の色相」の回転になる。1 枚の中の
                          色相差はそのまま保たれるので、中央のグラデーションなど
                          意図的な色相のズレを残せる。基準色相は下の
                          STORAGE_TEMPLATE_HUE / ACCELERATOR_TEMPLATE_HUE で宣言する
                          (協調処理ユニットは赤で描く前提にしてある)。

どちらの場合も彩度の無い画素は白のまま残るので、光の粒は白くできる。
アルファはそのまま維持されるので、抜きたい部分は透明にしておくこと。

発光レイヤは高さが 16 ならアニメ無し、16xN ならそのまま N コマとして扱う。
"""

from __future__ import annotations

import argparse
import colorsys
import json
import math
import os
from PIL import Image

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(REPO, "src/main/resources/assets/insaneae/textures/block/crafting")


# --------------------------------------------------------------------------------------
# 下敷きディレクトリの解決 (MC バージョンごとに分けられる)
# --------------------------------------------------------------------------------------


def read_minecraft_version(repo: str = REPO) -> str | None:
    """gradle.properties の minecraft_version を読む。読めなければ None。"""
    try:
        with open(os.path.join(repo, "gradle.properties"), encoding="utf-8") as fh:
            for line in fh:
                key, sep, value = line.partition("=")
                if sep and key.strip() == "minecraft_version":
                    return value.strip() or None
    except OSError:
        pass
    return None


def resolve_template_dirs(repo: str = REPO) -> list[str]:
    """下敷きを探す順番を決める。<b>ファイル単位</b>で前から順に見る。

        1. tools/textures/<minecraft_version>/   そのバージョン専用の絵
        2. tools/textures/                       バージョン共通の絵

    MC バージョンごとにブランチを分けている (main = 1.20.1 / 1.21.1) ので、
    gradle.properties を見れば<b>チェックアウトしているブランチに合った下敷きが
    自動で選ばれる</b>。

    探索がファイル単位なのが要点で、<b>バージョンで変えたい絵だけ</b>
    tools/textures/<version>/ に置けばよい。残りは tools/textures/ の共通の絵が
    そのまま使われるので、全部コピーして二重管理する必要はない。
    バージョン用のディレクトリが無ければ、従来どおり tools/textures/ だけを見る。
    """
    root = os.path.join(repo, "tools/textures")
    version = read_minecraft_version(repo)
    if version:
        versioned = os.path.join(root, version)
        if os.path.isdir(versioned):
            return [versioned, root]
    return [root]


TEMPLATE_DIRS = resolve_template_dirs()

# --------------------------------------------------------------------------------------
# 色の設定 — ここだけ触れば見た目が変わる
# --------------------------------------------------------------------------------------

# クラフトストレージの階層 (ModBlocks の登録順と同じ)。
STORAGE_TIERS = ["1g", "4g", "16g", "64g", "256g",
                 "1t", "4t", "16t", "64t", "256t",
                 "1p", "4p", "16p", "64p", "256p",
                 "1e", "4e", "8e"]

# 単位帯ごとの基準色。cell_component_<tier>.png (手描きのアイテムテクスチャ) から
# 拾った色に合わせてあるので、アイテムとブロックの色が揃う。
STORAGE_BAND_COLORS = {
    "g": "#ff42c6",   # マゼンタ
    "t": "#ff5c20",   # オレンジ
    # 黄緑。純粋な黄緑 (#d2ff20) は輝度を落とすとオリーブ/黄土色に濁るので、
    # 少し緑寄りに振ってある。振りすぎると e 帯の緑と見分けが付かなくなる。
    "p": "#b4ff20",
    "e": "#33ec4c",   # 緑
}
# 帯の中の例外 (最上段だけ別色にしたい等)。
STORAGE_TIER_OVERRIDES = {
}

# 協調処理ユニットの階層。
ACCELERATOR_TIERS = ["16x", "64x", "256x", "1k", "4k", "16k", "64k", "256k",
                     "1m", "4m", "16m", "64m", "256m", "1g", "2g"]
# 加速側は 1 色系。上位ほど明るく、少しだけ白に寄せる。
ACCELERATOR_COLOR = "#ff00ff"
ACCELERATOR_MAX_DESATURATION = 0.35   # 最上段でどこまで白に寄せるか

# --------------------------------------------------------------------------------------
# コントラスト — 中央の色が地に対してどれくらい暗くなるか
# --------------------------------------------------------------------------------------
# (帯の最下段, 帯の最上段) の目標<b>相対輝度</b>。地は明るいグレーなので、
# 中央は常にこの範囲まで落とすことで、どの帯でも同じくらいの読みやすさになる。
#
# HSV の明度ではなく輝度で指定しているのが要点 (fit_luminance の説明を参照)。
# 明度で揃えると、黄緑の帯だけ地より明るくなって沈んでしまう。
#
# 下げるほど中央が濃くコントラストが強くなる。地の絵を明るくしたら下げること。
#
# <b>ここで指定するのは「陰影の一番濃いところ」の輝度</b>であることに注意。
# 中央レイヤに陰影が描いてあると、明るい側はこの値の (テンプレの輝度比) 倍まで上がる。
# 今の下敷きは比 1.91 なので、0.32 を指定すると明るい側は約 0.61 になる。
# 地の輝度 (0.888) を超えないよう、上限は 0.33 くらいまでに留めること。
STORAGE_LUMA = (0.11, 0.28)
ACCELERATOR_LUMA = (0.11, 0.30)

# 帯ごとの上書き。<b>暗くすると濁る色は、ここで明るめに逃がす。</b>
# 黄緑や緑は輝度を落とすとオリーブ/黄土色になってしまうので、
# コントラストを少し譲ってでも明るい側に置いたほうが色が生きる
# (そのぶん地との差は小さくなるが、色相が地のグレーと大きく違うので読める)。
STORAGE_BAND_LUMA = {
    "p": (0.20, 0.32),   # 黄緑
    "e": (0.16, 0.30),   # 緑
}

# セル (アイテム) 側は暗い背景に置かれるので輝度は揃えない。
# 帯の中で下位ほど暗くする度合いだけを指定する (1.0 にすると帯の中は全部同じ明るさ)。
#
# セルコンポーネントは<b>明るい筐体の上に数ドットだけ色が乗る</b>デザインなので、
# 明るさを落とすと「濁った点」に見えてしまう。帯の中の階層差はドットの数と配置
# (5 パターン) でも付いているため、色は鮮やかさを優先して高めに寄せてある。
BAND_MIN_BRIGHTNESS = 0.88

# --------------------------------------------------------------------------------------
# 虹色にする階層 (最上段の特別扱い)
# --------------------------------------------------------------------------------------
# 単色ではなく画素の位置で色相を振る。セル側 (gen_cell_textures.py) の 8E と同じ扱い。
# ここに入れた階層は帯の位置計算から外れるので、足しても他の階層の色は動かない。
STORAGE_RAINBOW_TIERS = {"8e"}
ACCELERATOR_RAINBOW_TIERS = {"2g"}

# 虹の振り方は<b>種類ごとに変える</b>。地・中央・発光の下敷きを 2 種類で共用している
# (crafting_*.png) ので、ここまで同じにすると 8E ストレージと 2G 協調処理ユニットが
# 1 ドットも違わない絵になってしまう。基準色・振る向き・1 ドットあたりの回転量の
# 3 つをずらして、最上段どうしが並んでも別物と分かるようにしてある。
#
# axis は色相を振る方向で、rainbow_offset() が対応している 3 種類から選ぶ。
#   "diag"  x+y      左上→右下の斜めに流れる
#   "anti"  x-y      右上→左下の斜めに流れる
#   "ring"  中心からの距離  中心から外へ同心に広がる
RAINBOW_BASE = "#df00ff"     # ストレージ 8E の基準色。(x+y) == 6 の位置がこの色になる
RAINBOW_STEP = -1.0 / 32.0   # x+y が 1 増えるごとに回す色相
RAINBOW_AXIS = "diag"        # 斜めにゆっくり流れる虹

# 2G は<b>同じ基準色のまま振り方だけ変える</b>。中心から外へ 1 リングごとに 1/4 周ぶん
# 回すので、中央のピンクから シアン → 緑 → マゼンタ → 紫 と同心に広がる。
# 8E の「斜めにゆっくり流れる虹」とは絵として全く別物になる。
ACCELERATOR_RAINBOW_BASE = RAINBOW_BASE
ACCELERATOR_RAINBOW_STEP = -1.0 / 4.0
ACCELERATOR_RAINBOW_AXIS = "ring"

# --------------------------------------------------------------------------------------
# テンプレートを「何色で描いてあるか」
# --------------------------------------------------------------------------------------
# 中央 (color) と発光 (light) のテンプレートに色を付けて描く場合、その基準色相をここで宣言する。
# 宣言しておくと「その色相 → 階層色の色相」の回転になるので、1 枚の中でわざと色相を
# ずらした部分 (中央のグラデーションなど) が相対関係のまま保たれる。
#
# None にすると自動判定 (テンプレの主要色相を基準にする)。
# テンプレがグレースケールのままなら、どちらを指定しても今までどおり
# 「明度だけ残して階層色を乗せる」染め方になるので、塗り替える前に設定しても害はない。
#
# 値は色相 (0.0〜1.0)。RED / GREEN / BLUE を使うと分かりやすい。
RED, GREEN, BLUE = 0.0, 1.0 / 3.0, 2.0 / 3.0

STORAGE_TEMPLATE_HUE = None       # 自動判定
ACCELERATOR_TEMPLATE_HUE = RED    # 中央と発光は赤で描く

# 色相を持つ画素とみなす最小彩度。これ未満は「地のグレー / 白いハイライト」として扱う。
MIN_SAT = 0.15

# 「彩度が同じ画素」とみなす幅 (recolor の基準画素を選ぶときに使う)。
SAT_TIE = 0.05

# --------------------------------------------------------------------------------------
# 手続き描画のパラメータ (テンプレートが無いときに使う)
# --------------------------------------------------------------------------------------

EDGE = (0x19, 0x19, 0x19)         # 一番外側の枠
BEVEL_LIGHT = (0x6e, 0x6e, 0x6e)  # 上/左のハイライト
BEVEL_DARK = (0x2e, 0x2e, 0x2e)   # 下/右の影
CHASSIS = (0x4c, 0x4c, 0x4c)      # 筐体の地
RECESS = (0x23, 0x23, 0x23)       # 中央パネルの落とし込み枠

SIZE = 16

# 下敷きの共有名。ストレージと協調処理ユニットで同じ絵を使う場合はこの名前で置く
# (crafting_base.png など)。種類ごとに変えたいものだけ storage_* / accelerator_* を置けば、
# そちらが優先される。
SHARED_PREFIX = "crafting"

# 発光レイヤのコマ数。
#
# <b>AE2 は 1.21 でクラフトストレージの発光をアニメーション無しに変えた</b>
# (15.2.16 には block/crafting/*.mcmeta が 5 個あり 1k_storage_light.png は 2252 バイトだったが、
#  19.2.17 では mcmeta が 0 個で 203 バイトの静止画になっている)。
# 見た目を AE2 に合わせるため、こちらも 1 コマ = アニメーション無しにしてある。
#
# 2 以上にすると下敷きの先頭からそのコマ数を使い、.mcmeta も書き出す。
# 下敷きが 16xN で N がこれより多い場合は先頭のコマだけ使う。
LIGHT_FRAMES = 1

# 手続き描画で発光レイヤを描くときのコマ数 (下敷きが無いときの見た目用)。
FRAMES = max(8, LIGHT_FRAMES)

# 発光レイヤのドット絵 (# = 光る)
# ストレージ: 外周のリングから 4 本のリードが中央のチップに伸びる回路パターン。
STORAGE_MASK = [
    "................",
    "................",
    "..############..",
    "..#..........#..",
    "..#..........#..",
    "..#..........#..",
    "..#...####...#..",
    "..#####..#####..",
    "..#####..#####..",
    "..#...####...#..",
    "..#..........#..",
    "..#..........#..",
    "..#..........#..",
    "..############..",
    "................",
    "................",
]

# 協調処理ユニット: 上下向きの二重シェブロン (加速の記号)。
ACCELERATOR_MASK = [
    "................",
    "................",
    "......####......",
    ".....######.....",
    "....##....##....",
    "...##......##...",
    "..##........##..",
    "................",
    "................",
    "..##........##..",
    "...##......##...",
    "....##....##....",
    ".....######.....",
    "......####......",
    "................",
    "................",
]


def mcmeta(frames: int) -> dict:
    return {
        "animation": {
            "interpolate": True,
            "frames": [{"index": 0, "time": 100}] + [{"index": i, "time": 2} for i in range(1, frames)],
        }
    }


# --------------------------------------------------------------------------------------
# 色ユーティリティ
# --------------------------------------------------------------------------------------


def parse_hex(value: str) -> tuple[int, int, int]:
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def scale(color, factor: float, desaturate: float = 0.0):
    """明度を factor 倍し、desaturate のぶんだけ白に寄せる。"""
    h, s, v = colorsys.rgb_to_hsv(*[c / 255 for c in color])
    s = max(0.0, s * (1.0 - desaturate))
    v = min(1.0, v * factor)
    return tuple(round(c * 255) for c in colorsys.hsv_to_rgb(h, s, v))


def relative_luminance(rgb) -> float:
    """sRGB の相対輝度 (0.0〜1.0)。人が感じる明るさに近い。"""
    def lin(c: float) -> float:
        c /= 255
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (lin(c) for c in rgb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def fit_luminance(color, target: float) -> tuple[int, int, int]:
    """色相・彩度を保ったまま、<b>相対輝度が target になるよう</b>明度を合わせる。

    HSV の明度と「人が感じる明るさ」は色によって大きくずれる。
    たとえば同じ明度 1.0 でも 黄緑 #d2ff20 は輝度 0.85、マゼンタ #ff42c6 は 0.29 しかない。
    明度を比率で動かす方式だと、帯によって地とのコントラストが全く揃わず、
    明るい帯 (黄緑) は明るい地に溶けて見えなくなる。輝度で揃えればこれが起きない。

    彩度が高い色は明度を上げきっても target に届かないことがある
    (マゼンタ #ff42c6 は明度 1.0 でも輝度 0.292 が上限)。その場合は<b>彩度を落として</b>
    明るくする。これをやらないと帯の上位が全部同じ色になって階層の差が消える。
    """
    h, s0, _ = colorsys.rgb_to_hsv(*[c / 255 for c in color])

    def rgb_of(s: float, v: float):
        return tuple(round(c * 255) for c in colorsys.hsv_to_rgb(h, s, v))

    def search(make, lo: float, hi: float):
        """make(x) の輝度が target になる x を二分探索する (24 回で 8bit には十分)。"""
        for _ in range(24):
            mid = (lo + hi) / 2
            if relative_luminance(make(mid)) < target:
                lo = mid
            else:
                hi = mid
        return (lo + hi) / 2

    if relative_luminance(rgb_of(s0, 1.0)) >= target:
        return rgb_of(s0, search(lambda v: rgb_of(s0, v), 0.0, 1.0))
    # 明度は振り切っているので、ここから先は彩度を落として明るくする (パステル寄りになる)
    return rgb_of(search(lambda s: rgb_of(s, 1.0), s0, 0.0), 1.0)


def band_color_value(tier: str, tiers: list[str]) -> tuple[int, int, int]:
    """帯の色を<b>明度の比</b>で決める。セル (アイテム) 用。

    ブロックは明るい地の上に載るので輝度を揃える必要があるが
    ({@link band_color})、アイテムはインベントリの暗い背景に置かれるので、
    輝度を揃えると全体が沈んで濁ってしまう。
    アイテム側は色相ごとの<b>自然な明るさをそのまま活かす</b>ほうが鮮やかに出る。

    色相と彩度はブロック側と共通なので、同じ階層のブロックとアイテムは
    明るさが違うだけで同じ色味に見える。
    """
    if tier in STORAGE_TIER_OVERRIDES:
        return parse_hex(STORAGE_TIER_OVERRIDES[tier])
    if tier in STORAGE_RAINBOW_TIERS:
        return parse_hex(RAINBOW_BASE)
    band = tier[-1]
    in_band = [t for t in tiers if t[-1] == band
               and t not in STORAGE_TIER_OVERRIDES and t not in STORAGE_RAINBOW_TIERS]
    pos = in_band.index(tier) / max(1, len(in_band) - 1)
    return scale(parse_hex(STORAGE_BAND_COLORS[band]),
                 BAND_MIN_BRIGHTNESS + (1.0 - BAND_MIN_BRIGHTNESS) * pos)


def band_color(tier: str, tiers: list[str]) -> tuple[int, int, int]:
    """クラフトストレージ / セルの階層色。ブロックとアイテムで<b>共通</b>。

    帯 (g/t/p/e) ごとの基準色の色相・彩度を使い、帯の中の位置で輝度を上げていく。
    虹色の階層と個別指定の階層は帯の位置計算から外してあるので、
    それらを足しても他の階層の色は動かない。
    """
    if tier in STORAGE_TIER_OVERRIDES:
        return parse_hex(STORAGE_TIER_OVERRIDES[tier])
    if tier in STORAGE_RAINBOW_TIERS:
        return parse_hex(RAINBOW_BASE)
    band = tier[-1]
    in_band = [t for t in tiers if t[-1] == band
               and t not in STORAGE_TIER_OVERRIDES and t not in STORAGE_RAINBOW_TIERS]
    pos = in_band.index(tier) / max(1, len(in_band) - 1)
    lo, hi = STORAGE_BAND_LUMA.get(band, STORAGE_LUMA)
    return fit_luminance(parse_hex(STORAGE_BAND_COLORS[band]), lo + (hi - lo) * pos)


def storage_color(tier: str) -> tuple[int, int, int]:
    return band_color(tier, STORAGE_TIERS)


def accelerator_color(tier: str) -> tuple[int, int, int]:
    """協調処理ユニットの階層色。1 色系のまま、上位ほど明るく・少し白っぽくする。

    虹色の階層も<b>階層一覧からは外さない</b>ので、下位の色は虹色を足しても動かない。
    """
    if tier in ACCELERATOR_RAINBOW_TIERS:
        return parse_hex(ACCELERATOR_RAINBOW_BASE)
    pos = ACCELERATOR_TIERS.index(tier) / max(1, len(ACCELERATOR_TIERS) - 1)
    h, s, v = colorsys.rgb_to_hsv(*[c / 255 for c in parse_hex(ACCELERATOR_COLOR)])
    s *= 1.0 - ACCELERATOR_MAX_DESATURATION * pos
    toned = tuple(round(c * 255) for c in colorsys.hsv_to_rgb(h, s, v))
    lo, hi = ACCELERATOR_LUMA
    return fit_luminance(toned, lo + (hi - lo) * pos)


def dominant_hue(image: Image.Image, min_sat: float = MIN_SAT) -> tuple[float, float, float]:
    """テンプレートの「主要な色相」と、彩度のある画素の最大彩度・全体の最大明度を返す。

    <b>面積で決める</b>のが要点。色相を 72 分割したヒストグラムに彩度のある画素だけを
    入れ、一番画素数の多い色相を基準にする。「一番明るい画素」を基準にすると、
    白いハイライトが 1 点あるだけで全体の回転量がそれに引きずられてしまう
    (加速機の中央で意図しない色相のズレが出ていたのはこれが原因)。

    彩度のある画素が 1 つも無ければ彩度に 0.0 を返す = グレースケール扱い。
    """
    counts: dict[int, int] = {}
    peak_s = 0.0
    peak_v = 0.0
    for r, g, b, a in image.convert("RGBA").getdata():
        if a == 0:
            continue
        h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
        peak_v = max(peak_v, v)
        if s < min_sat:
            continue
        counts[round(h * 72) % 72] = counts.get(round(h * 72) % 72, 0) + 1
        peak_s = max(peak_s, s)
    if not counts:
        return 0.0, 0.0, peak_v
    return max(counts.items(), key=lambda kv: kv[1])[0] / 72.0, peak_s, peak_v


def recolor(template: Image.Image, target, source_hue: float | None = None) -> Image.Image:
    """テンプレートを階層色に染める。テンプレの描き方で 2 通りに分かれる。

    <b>グレースケールで描いた場合</b> (彩度のある画素が 1 つも無い)
        明度だけを残し、色相と彩度は target のものをそのまま乗せる。
        陰影だけ描けばよいので楽だが、1 枚の中で色相を変えることはできない。

    <b>色を付けて描いた場合</b>
        「テンプレの基準色相 → target の色相」の回転になる。
        1 枚の中の色相差は<b>相対関係のまま保たれる</b>ので、
        わざと色相をずらした陰影やグラデーションがそのまま出る。
        基準色相は source_hue で明示できる (省略時はテンプレの主要色相)。
        たとえば赤 (#ff0000) で描いて source_hue=0.0 を渡せば、
        「赤で描いた絵をそのまま階層色に回した」結果になる。

    どちらの場合も彩度の無い画素 (白いハイライトなど) は彩度 0 のまま残るので、
    光の粒などは白いままにできる。明度はテンプレの最大明度が target の明度に
    なるよう比率で合わせる。アルファはそのまま維持される。
    """
    src = template.convert("RGBA")
    px = list(src.getdata())
    if not any(p[3] > 0 for p in px):
        return src

    ref_h, ref_s, peak_v = dominant_hue(src)
    if source_hue is not None:
        ref_h = source_hue
    # 彩度のある画素が無ければ色相を回しようがないので、グレースケールとして扱う
    # (source_hue を指定してあっても同じ。テンプレを塗るまでは今までどおり動く)。
    greyscale = ref_s <= 0.0
    peak_v = peak_v or 1.0
    tgt_h, tgt_s, tgt_v = colorsys.rgb_to_hsv(*[c / 255 for c in target])

    # 色付きテンプレの陰影は「輝度の比」で残す。
    # 陰影を彩度で描いた絵 (濃い赤 → 薄い赤) を明度の比だけで写すと、
    # 色相によって輝度差になったりならなかったりして、階層によって
    # 陰影が消えて単色に見えてしまう (黄緑の帯で顕著だった)。
    #
    # 基準は「階層色そのものになる画素」= 彩度が一番高い画素。ただし<b>同じ彩度の画素が
    # 複数あるときは一番明るいものを採る</b>。ここを走査順まかせにすると、濃い影のほうが
    # 基準に選ばれた瞬間に本体の輝度比が 1 を大きく超え、fit_luminance が彩度を捨てて
    # <b>面全体が白く飛ぶ</b> (エネルギーセルの下敷きが彩度 0.55 の 2 色を持っていて実際に起きた)。
    ref_lum = 0.0
    if not greyscale:
        colored = [(colorsys.rgb_to_hsv(*[c / 255 for c in p[:3]])[1], relative_luminance(p[:3]))
                   for p in px if p[3] > 0
                   and colorsys.rgb_to_hsv(*[c / 255 for c in p[:3]])[1] >= MIN_SAT]
        if colored:
            top = max(s for s, _ in colored)
            # 彩度が僅かに違うだけの画素も同格とみなす (手描きだとぴったりは揃わない)。
            ref_lum = max(lum for s, lum in colored if s >= top - SAT_TIE)
    tgt_lum = relative_luminance(target)

    out = []
    for p in px:
        if p[3] == 0:
            out.append(p)
            continue
        h, s, v = colorsys.rgb_to_hsv(*[c / 255 for c in p[:3]])
        if greyscale:
            r, g, b = colorsys.hsv_to_rgb(tgt_h, tgt_s, min(1.0, v * (tgt_v / peak_v)))
            out.append((round(r * 255), round(g * 255), round(b * 255), p[3]))
            continue
        nh = (h + (tgt_h - ref_h)) % 1.0
        ns = min(1.0, s * (tgt_s / ref_s))
        if ref_lum > 0:
            want = min(1.0, tgt_lum * (relative_luminance(p[:3]) / ref_lum))
            r, g, b = fit_luminance(
                tuple(round(c * 255) for c in colorsys.hsv_to_rgb(nh, ns, 1.0)), want)
        else:
            r, g, b = (round(c * 255)
                       for c in colorsys.hsv_to_rgb(nh, ns, min(1.0, v * (tgt_v / peak_v))))
        out.append((r, g, b, p[3]))

    result = Image.new("RGBA", src.size)
    result.putdata(out)
    return result


def rainbow_offset(x: int, y: int, width: int, axis: str) -> float:
    """虹色を振るときの「その画素の位置」。axis で向きを選ぶ。

    ここで返した値に step を掛けたぶんだけ色相が回る。0 を返す位置が基準色になる。

        "diag"  x+y            左上→右下の斜め。0 は (x+y) == 6 の帯
        "anti"  x-y            右上→左下の斜め。0 は主対角線
        "ring"  中心からの距離  中心から外へ同心に広がる。0 は中央の 2x2

    y は<b>コマ内の位置</b>にしてある (発光レイヤが 16xN の縦並びでも、
    コマごとに同じ模様が出るように)。
    """
    row = y % width
    if axis == "anti":
        return x - row
    if axis == "ring":
        c = (width - 1) / 2
        return round(max(abs(x - c), abs(row - c)) - 0.5)
    return x + row - 6


def hue_rotate(template: Image.Image, target, rainbow: bool = False,
               min_sat: float = MIN_SAT, step: float | None = None,
               axis: str = RAINBOW_AXIS, keep_luma: bool = False) -> Image.Image:
    """色付きテンプレートの色相を回す。彩度の無い画素 (地のグレー) は触らない。

    「テンプレの主要色相 → target の色相」の回転なので、1 枚の中の色相差
    (濃い緑の陰など) は相対関係のまま保たれる。明度はテンプレの最大明度が
    target の明度になるよう比率で合わせる。

    rainbow=True にすると、そこからさらに画素の位置に応じて色相を振る (虹色の階層用)。
    振る向きは axis で選ぶ ({@link rainbow_offset})。

    keep_luma=True にすると、<b>色相を回しても相対輝度が変わらない</b>ようにする。
    彩度と明度を据え置いたまま色相だけ回すと、黄色は輝度 0.93・青は 0.07 と
    桁違いに明るさが変わるので、虹の輪が「金色に光る帯」と「黒く沈む帯」の
    まだら模様になってしまう。輝度を保つと色だけが変わる素直な虹になる。
    """
    src = template.convert("RGBA")
    ref_h, _, ref_v = dominant_hue(src, min_sat)
    tgt_h, tgt_s, tgt_v = colorsys.rgb_to_hsv(*[c / 255 for c in target])
    delta = tgt_h - ref_h
    gain = tgt_v / ref_v if ref_v > 0 else 1.0

    out = []
    width = src.width
    for i, (r, g, b, a) in enumerate(src.getdata()):
        if a == 0:
            out.append((r, g, b, a))
            continue
        h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
        if s < min_sat:
            out.append((r, g, b, a))       # 地のグレーはそのまま
            continue
        shift = delta
        if rainbow:
            shift += ((RAINBOW_STEP if step is None else step)
                      * rainbow_offset(i % width, i // width, width, axis))
        nv = min(1.0, v * gain)
        rotated = tuple(round(c * 255) for c in colorsys.hsv_to_rgb((h + shift) % 1.0, s, nv))
        if keep_luma:
            # 回す前の色 (= 色相だけ据え置いたもの) の輝度に合わせ直す。
            unrotated = tuple(round(c * 255) for c in colorsys.hsv_to_rgb(h, s, nv))
            rotated = fit_luminance(rotated, relative_luminance(unrotated))
        out.append((*rotated, a))

    result = Image.new("RGBA", src.size)
    result.putdata(out)
    return result


def rainbow_recolor(template: Image.Image, target, step: float | None = None,
                    source_hue: float | None = None,
                    axis: str = RAINBOW_AXIS, keep_luma: bool = False) -> Image.Image:
    """テンプレートを画素の位置で色相を振りながら染める。

    まず普通に染めてから色相を振るので、グレースケールで描いた下敷きでも虹色になる。
    step は「位置が 1 進むごとに回す色相」。光る部分が狭い絵ほど大きくしないと
    虹に見えないので、呼び出し側で指定できるようにしてある。
    axis は振る向き ({@link rainbow_offset})、keep_luma は輝度を保つかどうか
    (どちらも {@link hue_rotate} 参照)。
    """
    base = recolor(template, target, source_hue)
    return hue_rotate(base, target, rainbow=True, min_sat=0.05, step=step, axis=axis,
                      keep_luma=keep_luma)


# --------------------------------------------------------------------------------------
# 手続き描画 (テンプレートが無いときのフォールバック / --dump-templates の中身)
# --------------------------------------------------------------------------------------


def draw_base() -> Image.Image:
    """未 formed 面の下地。中央のアクセント部分は透明のまま空けておく。"""
    img = Image.new("RGBA", (SIZE, SIZE))
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            if 4 <= x <= 11 and 4 <= y <= 11:
                continue  # 色レイヤに任せる
            if x in (0, SIZE - 1) or y in (0, SIZE - 1):
                rgb = EDGE
            elif 3 <= x <= 12 and 3 <= y <= 12:
                rgb = RECESS
            elif x == 1 or y == 1:
                rgb = BEVEL_LIGHT
            elif x == SIZE - 2 or y == SIZE - 2:
                rgb = BEVEL_DARK
            else:
                rgb = CHASSIS if (x + y) % 2 else scale(CHASSIS, 1.06)
            px[x, y] = (*rgb, 255)
    return img


def draw_color_layer() -> Image.Image:
    """下地に重ねる色レイヤ。グレースケールの陰影だけを持つ 8x8。"""
    img = Image.new("RGBA", (SIZE, SIZE))
    px = img.load()
    for y in range(4, 12):
        for x in range(4, 12):
            # 中心から外に向かって暗くする (同心の四角いグラデーション)。
            d = max(abs(x - 7.5), abs(y - 7.5)) / 3.5
            factor = 1.0 - 0.34 * d
            factor += 0.035 if (x * 7 + y * 3) % 3 == 0 else 0.0
            level = round(min(1.0, factor) * 255)
            px[x, y] = (level, level, level, 255)
    return img


def mask_pixels(mask) -> list[tuple[int, int]]:
    return [(x, y) for y, row in enumerate(mask) for x, ch in enumerate(row) if ch == "#"]


def ring_path(mask) -> list[tuple[int, int]]:
    """光の粒を走らせる経路。マスクの一番外側の画素を時計回りに並べたもの。"""
    pts = mask_pixels(mask)
    if not pts:
        return []
    cx = sum(p[0] for p in pts) / len(pts)
    cy = sum(p[1] for p in pts) / len(pts)
    radius = max(max(abs(x - cx), abs(y - cy)) for x, y in pts)
    outer = [p for p in pts if max(abs(p[0] - cx), abs(p[1] - cy)) >= radius - 0.5]
    outer.sort(key=lambda p: math.atan2(p[1] - cy, p[0] - cx))
    return outer


def draw_light_layer(mask) -> Image.Image:
    """formed の発光レイヤ。グレースケール 8 コマ (16x128)。"""
    pts = mask_pixels(mask)
    path = ring_path(mask)
    inner = {p for p in pts if p not in set(path)}

    img = Image.new("RGBA", (SIZE, SIZE * FRAMES), (0, 0, 0, 0))
    px = img.load()
    for frame in range(FRAMES):
        offset = frame * SIZE
        for (x, y) in pts:
            # 光の粒 (255) を一番明るくしておくこと。グレースケールの下敷きは明度しか
            # 持てないので、粒を最大値にしないと染めたあと地の色に埋もれて見えなくなる。
            level = 228 if (x, y) in inner else 200
            px[x, y + offset] = (level, level, level, 255)
        # 外周を 4 つの光の粒が回る。
        if path:
            for j in range(4):
                idx = (frame * len(path) // FRAMES + j * len(path) // 4) % len(path)
                sx, sy = path[idx]
                px[sx, sy + offset] = (255, 255, 255, 255)
    return img


# --------------------------------------------------------------------------------------
# 生成
# --------------------------------------------------------------------------------------


def load_template(directory: str | list[str], name: str) -> Image.Image | None:
    """下敷きを 1 枚読む。ディレクトリを複数渡すと<b>前から順に</b>探す。"""
    for d in ([directory] if isinstance(directory, str) else directory):
        path = os.path.join(d, name)
        if os.path.isfile(path):
            return Image.open(path).convert("RGBA")
    return None


def write_dir(directory: str | list[str]) -> str:
    """--dump-templates の書き出し先。探索パスの先頭 (一番具体的なところ)。"""
    return directory if isinstance(directory, str) else directory[0]


class Kind:
    """ストレージ / 協調処理ユニットのどちらかぶんの下敷き一式。"""

    def __init__(self, prefix: str, mask, directory: str, template_hue: float | None = None,
                 rainbow_step: float = RAINBOW_STEP, rainbow_axis: str = RAINBOW_AXIS):
        self.prefix = prefix
        # 中央 (color) と発光 (light) を「何色で描いてあるか」。base は染めないので関係ない。
        self.template_hue = template_hue
        # 最上段の虹色の振り方。下敷きを 2 種類で共用しているので、
        # ここを変えないとストレージと協調処理ユニットの最上段が同じ絵になる。
        self.rainbow_step = rainbow_step
        self.rainbow_axis = rainbow_axis
        self.used = []
        self.base = self._pick(directory, "base", draw_base)
        self.color = self._pick(directory, "color", draw_color_layer)
        self.light = self._pick(directory, "light", lambda: draw_light_layer(mask))

    def _pick(self, directory, part: str, fallback):
        """下敷きを 1 枚選ぶ。<b>ディレクトリ優先</b>で、その中で専用名 → 共有名の順に見る。

            1. <dir>/<prefix>_<part>.png    その種類だけの絵 (storage_base.png など)
            2. <dir>/<SHARED>_<part>.png    ストレージと協調処理ユニットで共通の絵

        ディレクトリを先に回すのが要点。名前を先に回すと、バージョン別ディレクトリに
        共有名で置いても、共通ディレクトリの古い専用名の絵が勝ってしまう。
        """
        for d in ([directory] if isinstance(directory, str) else directory):
            for name in (f"{self.prefix}_{part}.png", f"{SHARED_PREFIX}_{part}.png"):
                image = load_template(d, name)
                if image is not None:
                    self.used.append(name)
                    return image
        self.used.append(f"{part} (手続き描画)")
        return fallback()

    def _paint(self, template: Image.Image, color, rainbow: bool) -> Image.Image:
        if rainbow:
            return rainbow_recolor(template, color, step=self.rainbow_step,
                                   source_hue=self.template_hue, axis=self.rainbow_axis)
        return recolor(template, color, self.template_hue)

    def face(self, color, rainbow: bool = False) -> Image.Image:
        # base はそのまま。地に若干色が付いていても染まらないので、
        # 無彩色で描く必要はない (階層に依らない部分はここに描く)。
        img = self.base.copy()
        img.alpha_composite(self._paint(self.color, color, rainbow))
        return img

    def light_for(self, color, rainbow: bool = False) -> tuple[Image.Image, int]:
        img = self._paint(self.light, color, rainbow)
        frames = min(max(1, img.height // img.width), LIGHT_FRAMES)
        # 下敷きが必要以上のコマを持っていたら先頭だけ切り出す
        # (LIGHT_FRAMES=1 なら 16x16 の静止画になり、.mcmeta も書かれない)。
        if img.height > img.width * frames:
            img = img.crop((0, 0, img.width, img.width * frames))
        return img, frames


def write(name: str, image: Image.Image, frames: int) -> None:
    path = os.path.join(OUT_DIR, name)
    image.save(path)
    meta = path + ".mcmeta"
    if frames > 1:
        with open(meta, "w", encoding="utf-8") as fh:
            json.dump(mcmeta(frames), fh, indent=2)
            fh.write("\n")
    elif os.path.exists(meta):
        os.remove(meta)


def dump_templates(directory: str | list[str]) -> None:
    directory = write_dir(directory)      # 書き出すのは探索パスの先頭
    os.makedirs(directory, exist_ok=True)
    # 地と中央は 2 種類で同じ絵なので共有名で 1 組だけ書き出す。
    draw_base().save(os.path.join(directory, f"{SHARED_PREFIX}_base.png"))
    draw_color_layer().save(os.path.join(directory, f"{SHARED_PREFIX}_color.png"))
    # 発光は模様 (マスク) が種類ごとに違うので専用名で書き出す。
    for prefix, mask in (("storage", STORAGE_MASK), ("accelerator", ACCELERATOR_MASK)):
        light = draw_light_layer(mask)
        if light.height > light.width * LIGHT_FRAMES:
            light = light.crop((0, 0, light.width, light.width * LIGHT_FRAMES))
        light.save(os.path.join(directory, f"{prefix}_light.png"))
    print(f"下敷きを書き出した -> {directory}\n"
          f"編集して置いておけば次回から使われる (消せば手続き描画に戻る)\n"
          f"{SHARED_PREFIX}_*.png はストレージと協調処理ユニットの共用。\n"
          f"種類ごとに変えたいものだけ storage_*.png / accelerator_*.png を置けばそちらが優先される。")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--templates", default=TEMPLATE_DIRS,
                        help="ベース/色/発光テクスチャを探すディレクトリ")
    parser.add_argument("--dump-templates", action="store_true",
                        help="いまの見た目を編集用の下敷きとして書き出して終了")
    args = parser.parse_args()

    # どの下敷きを使ったのか毎回出す (バージョンごとに分けていると取り違えやすいので)。
    print(f"下敷きディレクトリ: {args.templates}"
          f"  (gradle.properties の minecraft_version = {read_minecraft_version() or '不明'})")

    if args.dump_templates:
        dump_templates(args.templates)
        return

    os.makedirs(OUT_DIR, exist_ok=True)
    storage = Kind("storage", STORAGE_MASK, args.templates, STORAGE_TEMPLATE_HUE,
                   RAINBOW_STEP, RAINBOW_AXIS)
    accelerator = Kind("accelerator", ACCELERATOR_MASK, args.templates, ACCELERATOR_TEMPLATE_HUE,
                       ACCELERATOR_RAINBOW_STEP, ACCELERATOR_RAINBOW_AXIS)
    print(f"下敷き: storage={storage.used} accelerator={accelerator.used}\n")

    for tier in STORAGE_TIERS:
        color = storage_color(tier)
        rainbow = tier in STORAGE_RAINBOW_TIERS
        write(f"{tier}_storage.png", storage.face(color, rainbow), 1)
        write(f"{tier}_storage_light.png", *storage.light_for(color, rainbow))
        print(f"{tier + '_storage':16s} #{color[0]:02x}{color[1]:02x}{color[2]:02x}"
              f"{' (虹)' if rainbow else ''}")
    for tier in ACCELERATOR_TIERS:
        color = accelerator_color(tier)
        rainbow = tier in ACCELERATOR_RAINBOW_TIERS
        write(f"{tier}_accelerator.png", accelerator.face(color, rainbow), 1)
        write(f"{tier}_accelerator_light.png", *accelerator.light_for(color, rainbow))
        print(f"{tier + '_accelerator':16s} #{color[0]:02x}{color[1]:02x}{color[2]:02x}"
              f"{' (虹)' if rainbow else ''}")
    print(f"\n-> {OUT_DIR}")


if __name__ == "__main__":
    main()
