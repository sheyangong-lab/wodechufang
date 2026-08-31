#!/usr/bin/env python3
"""生成「我的」页宫格图标 —— 与既有图标同风格：96x96 RGBA、暖灰主色 + 琥珀点缀。

用法: cd client && python3 ../scripts/gen-profile-icons.py
4x 超采样绘制后缩小到 96px，线条圆润。
"""
from PIL import Image, ImageDraw
import os

GRAY = (150, 135, 113, 255)   # 暖灰，同 basket/receipt 图标
AMBER = (240, 166, 60, 255)   # 琥珀，同 chefhat
SIZE = 96
SS = 4  # supersample
W = SIZE * SS
LW = 6 * SS  # 线宽


def canvas():
    im = Image.new('RGBA', (W, W), (0, 0, 0, 0))
    return im, ImageDraw.Draw(im)


def rounded_line(d, pts, color, width=LW):
    d.line(pts, fill=color, width=width, joint='curve')
    r = width // 2
    for x, y in (pts[0], pts[-1]):
        d.ellipse([x - r, y - r, x + r, y + r], fill=color)


def save(im, name):
    im = im.resize((SIZE, SIZE), Image.LANCZOS)
    out = os.path.join(os.path.dirname(__file__), '..', 'client', 'src', 'static', 'icons')
    im.save(os.path.join(out, name + '.png'))
    print('✓', name)


def clipboard():
    im, d = canvas()
    # 板身
    d.rounded_rectangle([20*SS, 14*SS, 76*SS, 84*SS], radius=10*SS, outline=GRAY, width=LW)
    # 板夹
    d.rounded_rectangle([38*SS, 8*SS, 58*SS, 22*SS], radius=6*SS, fill=AMBER)
    # 清单
    rounded_line(d, [(32*SS, 38*SS), (40*SS, 38*SS)], AMBER)
    rounded_line(d, [(48*SS, 38*SS), (66*SS, 38*SS)], GRAY)
    rounded_line(d, [(32*SS, 52*SS), (40*SS, 52*SS)], AMBER)
    rounded_line(d, [(48*SS, 52*SS), (66*SS, 52*SS)], GRAY)
    rounded_line(d, [(32*SS, 66*SS), (40*SS, 66*SS)], AMBER)
    rounded_line(d, [(48*SS, 66*SS), (66*SS, 66*SS)], GRAY)
    save(im, 'grid-mission')


def calendar():
    im, d = canvas()
    d.rounded_rectangle([14*SS, 20*SS, 82*SS, 84*SS], radius=10*SS, outline=GRAY, width=LW)
    rounded_line(d, [(14*SS, 38*SS), (82*SS, 38*SS)], GRAY)
    rounded_line(d, [(32*SS, 12*SS), (32*SS, 26*SS)], GRAY)
    rounded_line(d, [(64*SS, 12*SS), (64*SS, 26*SS)], GRAY)
    # 打勾的日子（琥珀）
    rounded_line(d, [(32*SS, 52*SS), (40*SS, 60*SS), (56*SS, 46*SS)], AMBER)
    save(im, 'grid-calendar')


def coin():
    im, d = canvas()
    d.ellipse([16*SS, 16*SS, 80*SS, 80*SS], outline=AMBER, width=LW)
    # ¥
    rounded_line(d, [(38*SS, 34*SS), (48*SS, 50*SS), (58*SS, 34*SS)], GRAY)
    rounded_line(d, [(48*SS, 50*SS), (48*SS, 66*SS)], GRAY)
    rounded_line(d, [(38*SS, 54*SS), (58*SS, 54*SS)], GRAY)
    rounded_line(d, [(38*SS, 62*SS), (58*SS, 62*SS)], GRAY)
    save(im, 'grid-coin')


def chart():
    im, d = canvas()
    rounded_line(d, [(18*SS, 14*SS), (18*SS, 80*SS), (82*SS, 80*SS)], GRAY)
    d.rounded_rectangle([30*SS, 52*SS, 42*SS, 78*SS], radius=4*SS, fill=GRAY)
    d.rounded_rectangle([48*SS, 38*SS, 60*SS, 78*SS], radius=4*SS, fill=AMBER)
    d.rounded_rectangle([66*SS, 26*SS, 78*SS, 78*SS], radius=4*SS, fill=GRAY)
    save(im, 'grid-chart')


def book():
    im, d = canvas()
    # 打开的书
    rounded_line(d, [(48*SS, 24*SS), (20*SS, 18*SS), (20*SS, 74*SS), (48*SS, 78*SS)], GRAY)
    rounded_line(d, [(48*SS, 24*SS), (76*SS, 18*SS), (76*SS, 74*SS), (48*SS, 78*SS)], GRAY)
    rounded_line(d, [(48*SS, 24*SS), (48*SS, 78*SS)], GRAY)
    # 书签
    rounded_line(d, [(62*SS, 22*SS), (62*SS, 42*SS)], AMBER)
    save(im, 'grid-book')


def chat():
    im, d = canvas()
    d.rounded_rectangle([12*SS, 18*SS, 84*SS, 64*SS], radius=14*SS, outline=GRAY, width=LW)
    # 气泡尾巴
    d.polygon([(28*SS, 62*SS), (28*SS, 82*SS), (46*SS, 62*SS)], fill=GRAY)
    # 对话点
    for i, x in enumerate((34, 48, 62)):
        color = AMBER if i == 1 else GRAY
        d.ellipse([(x-4)*SS, (37-4)*SS, (x+4)*SS, (37+4)*SS], fill=color)
    save(im, 'grid-chat')


def headset():
    im, d = canvas()
    d.arc([18*SS, 20*SS, 78*SS, 72*SS], start=180, end=360, fill=GRAY, width=LW)
    d.rounded_rectangle([14*SS, 50*SS, 28*SS, 72*SS], radius=6*SS, fill=AMBER)
    d.rounded_rectangle([68*SS, 50*SS, 82*SS, 72*SS], radius=6*SS, fill=AMBER)
    rounded_line(d, [(75*SS, 72*SS), (75*SS, 80*SS), (52*SS, 80*SS)], GRAY)
    d.ellipse([46*SS, 76*SS, 58*SS, 86*SS], fill=GRAY)
    save(im, 'grid-headset')


def dots():
    im, d = canvas()
    for yi, y in enumerate((28, 48, 68)):
        for xi, x in enumerate((28, 48, 68)):
            color = AMBER if (xi == 2 and yi == 2) else GRAY
            r = 7 * SS
            d.ellipse([x*SS-r, y*SS-r, x*SS+r, y*SS+r], fill=color)
    save(im, 'grid-dots')


def moon():
    im, d = canvas()
    d.ellipse([18*SS, 14*SS, 78*SS, 74*SS], fill=AMBER)
    # 用背景色抠出月牙（透明叠加）
    cutter = ImageDraw.Draw(im)
    cutter.ellipse([34*SS, 6*SS, 92*SS, 64*SS], fill=(0, 0, 0, 0))
    # 小星星
    d2 = ImageDraw.Draw(im)
    d2.ellipse([60*SS, 62*SS, 72*SS, 74*SS], fill=GRAY)
    save(im, 'grid-moon')


def server():
    im, d = canvas()
    d.rounded_rectangle([16*SS, 16*SS, 80*SS, 80*SS], radius=8*SS, outline=GRAY, width=LW)
    d.rounded_rectangle([16*SS, 52*SS, 80*SS, 80*SS], radius=8*SS, outline=GRAY, width=LW)
    d.ellipse([26*SS, 26*SS, 36*SS, 36*SS], fill=AMBER)
    d.ellipse([26*SS, 62*SS, 36*SS, 72*SS], fill=AMBER)
    rounded_line(d, [(46*SS, 30*SS), (70*SS, 30*SS)], GRAY)
    rounded_line(d, [(46*SS, 66*SS), (70*SS, 66*SS)], GRAY)
    save(im, 'grid-server')


def pan(color_body, color_accent, name):
    """斜 45° 平底锅（俯视）：锅体圆 + 右上手柄 + 锅心蛋黄点。"""
    im, d = canvas()
    cx, cy, r = 40 * SS, 56 * SS, 24 * SS
    # 手柄：从锅沿伸向右上，圆头粗线
    rounded_line(d,
                 [(cx + int(r*0.71), cy - int(r*0.71)), (78*SS, 18*SS)],
                 color_body, width=7*SS)
    # 锅体
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=color_body, width=LW)
    # 锅心蛋黄（点缀色）
    yr = 8 * SS
    d.ellipse([cx - yr, cy - yr, cx + yr, cy + yr], fill=color_accent)
    save(im, name)


def pan3d(name, rim, body, body_in, hi, dark, egg_white, yolk, yolk_hi, shadow):
    """斜 45° 平底锅 · 伪3D 版。

    俯视光影：左上光源 → 锅沿深色、锅内面左亮右暗、
    右下落影、左上高光弧、手柄上亮下暗 + 根部铆钉。
    全部用多层椭圆/弧线近似径向光影。
    """
    im, d = canvas()
    cx, cy, r = 38 * SS, 58 * SS, 23 * SS

    # ---- 手柄（先画，压在锅沿下面）----
    hx1, hy1 = cx + int(r*0.72), cy - int(r*0.72)   # 锅沿起点
    hx2, hy2 = 80 * SS, 16 * SS                      # 末端
    # 手柄落影
    rounded_line(d, [(hx1 + 2*SS, hy1 + 3*SS), (hx2 + 2*SS, hy2 + 3*SS)], shadow, width=8*SS)
    # 手柄暗面（下侧粗线）
    rounded_line(d, [(hx1, hy1 + 1*SS), (hx2, hy2 + 1*SS)], dark, width=8*SS)
    # 手柄亮面（上侧细线，留出暗边形成圆柱感）
    rounded_line(d, [(hx1, hy1 - 1*SS), (hx2, hy2 - 1*SS)], body, width=4*SS)
    # 手柄末端高光点
    d.ellipse([hx2 - 2*SS, hy2 - 4*SS, hx2 + 2*SS, hy2], fill=hi)
    # 手柄根部铆钉
    rv = 3 * SS
    d.ellipse([hx1 - rv, hy1 - rv, hx1 + rv, hy1 + rv], fill=rim)
    d.ellipse([hx1 - rv + 1*SS, hy1 - rv + 1*SS, hx1 + rv, hy1 + rv], fill=hi)

    # ---- 锅体 ----
    # 落影（右下偏移）
    d.ellipse([cx - r + 3*SS, cy - r + 4*SS, cx + r + 3*SS, cy + r + 4*SS], fill=shadow)
    # 锅沿（外圈，深色环）
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=rim)
    # 锅内面（缩进环宽）
    inset = 5 * SS
    d.ellipse([cx - r + inset, cy - r + inset, cx + r - inset, cy + r - inset], fill=dark)
    # 内面亮区（再缩进一层，向左上偏移 → 右下留暗边形成凹陷光影）
    d.ellipse([cx - r + inset + 1*SS, cy - r + inset, cx + r - inset - 1*SS, cy + r - inset - 2*SS], fill=body)
    d.ellipse([cx - r + inset + 2*SS, cy - r + inset, cx + r - inset - 3*SS, cy + r - inset - 4*SS], fill=body_in)
    # 左上高光弧（锅内面左上缘）
    d.arc([cx - r + inset + 1*SS, cy - r + inset + 1*SS,
           cx + r - inset - 1*SS, cy + r - inset - 1*SS],
          start=200, end=305, fill=hi, width=3*SS)
    # 锅沿左上高光
    d.arc([cx - r, cy - r, cx + r, cy + r], start=210, end=300, fill=hi, width=2*SS)

    # ---- 煎蛋（蛋白 + 蛋黄 + 蛋黄高光）----
    ex, ey = cx - 2*SS, cy + 1*SS
    # 蛋白（不规则圆：两瓣椭圆叠加）
    d.ellipse([ex - 11*SS, ey - 9*SS, ex + 11*SS, ey + 10*SS], fill=egg_white)
    d.ellipse([ex - 13*SS, ey - 4*SS, ex + 8*SS, ey + 9*SS], fill=egg_white)
    d.ellipse([ex - 6*SS, ey - 12*SS, ex + 12*SS, ey + 4*SS], fill=egg_white)
    # 蛋白暗边（右下细弧）
    d.arc([ex - 11*SS, ey - 9*SS, ex + 11*SS, ey + 10*SS], start=30, end=140, fill=dark, width=2*SS)
    # 蛋黄
    yr = 6 * SS
    d.ellipse([ex - yr, ey - yr - 1*SS, ex + yr, ey + yr - 1*SS], fill=yolk)
    # 蛋黄高光点
    d.ellipse([ex - 3*SS, ey - 4*SS, ex - 1*SS, ey - 2*SS], fill=yolk_hi)
    save(im, name)


def book(color_body, color_accent, name):
    """书本图标（食本 tab）：打开的书页 + 书签。"""
    im, d = canvas()
    # 左页
    d.polygon([(16*SS, 20*SS), (48*SS, 30*SS), (48*SS, 82*SS), (16*SS, 72*SS)], outline=color_body, width=LW)
    # 右页
    d.polygon([(80*SS, 20*SS), (48*SS, 30*SS), (48*SS, 82*SS), (80*SS, 72*SS)], outline=color_body, width=LW)
    # 中缝
    rounded_line(d, [(48*SS, 30*SS), (48*SS, 82*SS)], color_body, width=4*SS)
    # 书签
    d.polygon([(62*SS, 26*SS), (74*SS, 22*SS), (74*SS, 42*SS), (68*SS, 36*SS), (62*SS, 42*SS)], fill=color_accent)
    save(im, name)


if __name__ == '__main__':
    clipboard()
    calendar()
    coin()
    chart()
    chat()
    headset()
    dots()
    moon()
    server()
    # 平底锅：扁平灰版（保留备用）+ 伪3D 双色版（正式使用）
    pan(GRAY, AMBER, 'pan')
    pan(AMBER, GRAY, 'pan-active')
    # 食本 tab 图标：书本（灰=未选中 / 琥珀=选中）
    book((150, 135, 113, 255), (150, 135, 113, 255), 'tab-book')
    book((239, 166, 60, 255), (255, 224, 160, 255), 'tab-book-active')
    # 伪3D 常规版：暖灰锅体 + 琥珀蛋黄
    pan3d('pan',
          rim=(111, 98, 80, 255),        # 锅沿深暖灰
          body=(150, 135, 113, 255),     # 锅内面中灰
          body_in=(169, 155, 134, 255),  # 内面亮区
          hi=(206, 195, 175, 255),       # 高光
          dark=(126, 113, 94, 255),      # 暗面
          egg_white=(255, 251, 242, 255),
          yolk=AMBER,
          yolk_hi=(255, 226, 170, 255),
          shadow=(111, 98, 80, 70))
    # 伪3D 选中版：琥珀锅体 + 奶白蛋白
    pan3d('pan-active',
          rim=(168, 111, 28, 255),       # 深琥珀沿
          body=(239, 166, 60, 255),
          body_in=(248, 193, 92, 255),
          hi=(255, 224, 160, 255),
          dark=(203, 137, 40, 255),
          egg_white=(255, 251, 242, 255),
          yolk=(255, 255, 255, 255),
          yolk_hi=(239, 166, 60, 255),
          shadow=(168, 111, 28, 70))
