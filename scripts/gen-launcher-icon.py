#!/usr/bin/env python3
"""自定义安卓启动图标：一张原图 → 全套 mipmap 尺寸 + 自适应图标前景。

用法:
  python3 scripts/gen-launcher-icon.py <原图路径> [--bg 颜色]

- 原图建议 ≥1024x1024 正方形（非正方形自动居中裁方）；
- 生成 ic_launcher.png / ic_launcher_round.png（各 dpi）+ 自适应前景层；
- 自适应图标安全区只有中间 66%，脚本自动缩放到安全区内、四周留白；
- --bg 自适应图标底色（默认保持现有暖黄 #FFFBF2）。
生成后重新出包（build:h5 不需要，cap sync + gradle 即可）。
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageOps

RES = Path(__file__).resolve().parent.parent / "client/android/app/src/main/res"

# mipmap dpi → 像素
DENSITIES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}
# 自适应前景层尺寸（含安全区外留白，logo 占中间 66%）
FG_SIZES = {
    "mipmap-mdpi": 108,
    "mipmap-hdpi": 162,
    "mipmap-xhdpi": 216,
    "mipmap-xxhdpi": 324,
    "mipmap-xxxhdpi": 432,
}


def center_square(img: Image.Image) -> Image.Image:
    w, h = img.size
    side = min(w, h)
    return img.crop(((w - side) // 2, (h - side) // 2, (w + side) // 2, (h + side) // 2))


def rounded(img: Image.Image, size: int) -> Image.Image:
    im = img.resize((size, size), Image.LANCZOS).convert("RGBA")
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    # Android 圆角比例 ~17%
    r = int(size * 0.17)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=r, fill=255)
    im.putalpha(mask)
    return im


def main() -> None:
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    src = Path(sys.argv[1]).expanduser()
    if not src.exists():
        print(f"找不到图片: {src}")
        sys.exit(1)
    bg = "#FFFBF2"
    if "--bg" in sys.argv:
        bg = sys.argv[sys.argv.index("--bg") + 1]

    img = Image.open(src).convert("RGBA")
    square = center_square(img)
    square.save(RES.parent / "ic_launcher_src.png")  # 留底稿方便下次微调

    for dpi, size in DENSITIES.items():
        d = RES / dpi
        d.mkdir(parents=True, exist_ok=True)
        # 普通图标：暖黄底 + 圆角裁切
        base = Image.new("RGBA", (size, size), bg)
        fg = square.resize((size, size), Image.LANCZOS)
        base.paste(fg, (0, 0), fg)
        base.save(d / "ic_launcher.png")
        rounded(square, size).save(d / "ic_launcher_round.png")
        # 自适应前景：居中 66% 安全区
        fg_full = Image.new("RGBA", (FG_SIZES[dpi], FG_SIZES[dpi]), (0, 0, 0, 0))
        inner = int(FG_SIZES[dpi] * 0.66)
        logo = square.resize((inner, inner), Image.LANCZOS)
        off = (FG_SIZES[dpi] - inner) // 2
        fg_full.paste(logo, (off, off), logo)
        fg_full.save(d / "ic_launcher_foreground.png")

    print(f"✓ 启动图标已生成（源图 {src.name}）")
    print("  下一步出包: cd client && npx cap sync android && cd android && "
          "JAVA_HOME=java-17 ./gradlew assembleDebug")
    print("  注意: 安卓换启动图标后, 部分桌面需要重装/刷新才显示新图标")


if __name__ == "__main__":
    main()
