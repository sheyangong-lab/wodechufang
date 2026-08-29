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
    d.rounded_rectangle([16*SS, 16*SS, 80*SS, 44*SS], radius=8*SS, outline=GRAY, width=LW)
    d.rounded_rectangle([16*SS, 52*SS, 80*SS, 80*SS], radius=8*SS, outline=GRAY, width=LW)
    d.ellipse([26*SS, 26*SS, 36*SS, 36*SS], fill=AMBER)
    d.ellipse([26*SS, 62*SS, 36*SS, 72*SS], fill=AMBER)
    rounded_line(d, [(46*SS, 30*SS), (70*SS, 30*SS)], GRAY)
    rounded_line(d, [(46*SS, 66*SS), (70*SS, 66*SS)], GRAY)
    save(im, 'grid-server')


if __name__ == '__main__':
    clipboard()
    calendar()
    coin()
    chart()
    book()
    chat()
    headset()
    dots()
    moon()
    server()
