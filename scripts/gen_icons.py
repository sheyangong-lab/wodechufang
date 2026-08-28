#!/usr/bin/env python3
"""生成客户端 UI 图标（线性风格 PNG，禁用 emoji）。
输出：client/src/static/icons/
- tab-*.png        81x81，tabBar 用（灰/琥珀双态）
- 内联小图标        96px（search/cart/qr/chefhat/person/basket）
- empty-*.png      192px 浅灰，空状态插画
用法：python3 scripts/gen_icons.py
"""
from PIL import Image, ImageDraw, ImageFilter, ImageChops
import os

S = 648  # 超采样画布（8x of 81）
OUT = os.path.join(os.path.dirname(__file__), "..", "client", "src", "static", "icons")

GRAY = (140, 127, 106, 255)      # #8C7F6A 未选中
ACTIVE = (239, 166, 60, 255)     # #EFA63C 选中/主色
LIGHT = (207, 197, 178, 255)     # #CFC5B2 空状态


def u(v: float) -> int:
    return int(round(v * S))


def canvas() -> Image.Image:
    return Image.new("RGBA", (S, S), (0, 0, 0, 0))


def save(img: Image.Image, name: str, size: int):
    img = img.resize((size, size), Image.LANCZOS)
    path = os.path.join(OUT, name)
    img.save(path)
    print(f"  {name}  {size}x{size}  {os.path.getsize(path)}B")


def stroke(width: float):
    return max(3, u(width))


# ---------- 图标绘制（坐标均为 0..1 归一化） ----------

def draw_chef_hat_mask(color) -> Image.Image:
    """厨师帽：填充剪影 → 腐蚀取边缘，得到单一轮廓线。"""
    m = Image.new("L", (S, S), 0)
    dm = ImageDraw.Draw(m)
    dm.ellipse((u(0.20), u(0.24), u(0.46), u(0.52)), fill=255)
    dm.ellipse((u(0.28), u(0.10), u(0.72), u(0.56)), fill=255)
    dm.ellipse((u(0.54), u(0.24), u(0.80), u(0.52)), fill=255)
    dm.rectangle((u(0.30), u(0.42), u(0.70), u(0.64)), fill=255)
    dm.rounded_rectangle((u(0.26), u(0.62), u(0.74), u(0.82)), radius=u(0.05), fill=255)
    edge = ImageChops.subtract(m, m.filter(ImageFilter.MinFilter(stroke(0.042) | 1)))
    img = canvas()
    solid = Image.new("RGBA", (S, S), color)
    img.paste(solid, (0, 0), edge)
    return img


def draw_pot(color) -> Image.Image:
    img = canvas()
    d = ImageDraw.Draw(img)
    w = stroke(0.045)
    d.arc((u(0.24), u(0.14), u(0.76), u(0.58)), start=180, end=360, fill=color, width=w)
    d.line((u(0.24), u(0.36), u(0.76), u(0.36)), fill=color, width=w)
    d.ellipse((u(0.44), u(0.06), u(0.56), u(0.16)), outline=color, width=w)
    d.rounded_rectangle((u(0.16), u(0.36), u(0.84), u(0.78)), radius=u(0.07), outline=color, width=w)
    return img


def draw_receipt(color) -> Image.Image:
    img = canvas()
    d = ImageDraw.Draw(img)
    w = stroke(0.045)
    d.rounded_rectangle((u(0.22), u(0.08), u(0.78), u(0.92)), radius=u(0.09), outline=color, width=w)
    for y in (0.30, 0.50, 0.70):
        d.line((u(0.36), u(y), u(0.64), u(y)), fill=color, width=stroke(0.038))
    return img


def draw_ledger(color) -> Image.Image:
    img = canvas()
    d = ImageDraw.Draw(img)
    w = stroke(0.045)
    d.rounded_rectangle((u(0.16), u(0.08), u(0.84), u(0.92)), radius=u(0.09), outline=color, width=w)
    d.line((u(0.38), u(0.08), u(0.38), u(0.92)), fill=color, width=stroke(0.038))
    for y in (0.30, 0.50, 0.70):
        d.line((u(0.54), u(y), u(0.70), u(y)), fill=color, width=stroke(0.038))
    return img


def draw_fridge(color) -> Image.Image:
    img = canvas()
    d = ImageDraw.Draw(img)
    w = stroke(0.045)
    d.rounded_rectangle((u(0.26), u(0.05), u(0.74), u(0.95)), radius=u(0.10), outline=color, width=w)
    d.line((u(0.26), u(0.38), u(0.74), u(0.38)), fill=color, width=stroke(0.038))
    d.line((u(0.62), u(0.14), u(0.62), u(0.28)), fill=color, width=stroke(0.038))
    d.line((u(0.62), u(0.48), u(0.62), u(0.62)), fill=color, width=stroke(0.038))
    return img


def draw_person(color) -> Image.Image:
    img = canvas()
    d = ImageDraw.Draw(img)
    w = stroke(0.045)
    d.ellipse((u(0.35), u(0.08), u(0.65), u(0.38)), outline=color, width=w)
    d.arc((u(0.16), u(0.46), u(0.84), u(1.10)), start=180, end=360, fill=color, width=w)
    return img


def draw_search(color) -> Image.Image:
    img = canvas()
    d = ImageDraw.Draw(img)
    w = stroke(0.05)
    d.ellipse((u(0.16), u(0.16), u(0.60), u(0.60)), outline=color, width=w)
    d.line((u(0.56), u(0.56), u(0.84), u(0.84)), fill=color, width=stroke(0.055))
    return img


def draw_cart(color) -> Image.Image:
    img = canvas()
    d = ImageDraw.Draw(img)
    w = stroke(0.045)
    d.line([(u(0.08), u(0.16)), (u(0.20), u(0.16)), (u(0.32), u(0.62))], fill=color, width=w, joint="curve")
    d.line([(u(0.24), u(0.28), ), (u(0.90), u(0.28))], fill=color, width=w)
    d.line([(u(0.30), u(0.62), ), (u(0.82), u(0.62))], fill=color, width=w)
    d.line((u(0.24), u(0.28), u(0.30), u(0.62)), fill=color, width=w)
    d.line((u(0.90), u(0.28), u(0.82), u(0.62)), fill=color, width=w)
    d.ellipse((u(0.36), u(0.72), u(0.46), u(0.82)), outline=color, width=stroke(0.035))
    d.ellipse((u(0.66), u(0.72), u(0.76), u(0.82)), outline=color, width=stroke(0.035))
    return img


def draw_qr(color) -> Image.Image:
    img = canvas()
    d = ImageDraw.Draw(img)
    w = stroke(0.045)
    def corner(x, y):
        d.rounded_rectangle((u(x), u(y), u(x + 0.28), u(y + 0.28)), radius=u(0.04), outline=color, width=w)
        d.rectangle((u(x + 0.09), u(y + 0.09), u(x + 0.19), u(y + 0.19)), fill=color)
    corner(0.08, 0.08)
    corner(0.64, 0.08)
    corner(0.08, 0.64)
    for x, y in ((0.64, 0.64), (0.78, 0.64), (0.64, 0.78), (0.78, 0.78)):
        d.rectangle((u(x), u(y), u(x + 0.10), u(y + 0.10)), fill=color)
    return img


def draw_basket(color) -> Image.Image:
    img = canvas()
    d = ImageDraw.Draw(img)
    w = stroke(0.045)
    d.arc((u(0.30), u(0.10), u(0.70), u(0.50)), start=180, end=360, fill=color, width=w)
    d.line((u(0.16), u(0.38), u(0.84), u(0.38)), fill=color, width=w)
    d.line([(u(0.16), u(0.38)), (u(0.26), u(0.88)), (u(0.74), u(0.88)), (u(0.84), u(0.38))],
           fill=color, width=w, joint="curve")
    return img


def draw_heart(color) -> Image.Image:
    """爱心：两圆+三角剪影 → 腐蚀取边缘。"""
    m = Image.new("L", (S, S), 0)
    dm = ImageDraw.Draw(m)
    dm.ellipse((u(0.12), u(0.16), u(0.52), u(0.52)), fill=255)
    dm.ellipse((u(0.48), u(0.16), u(0.88), u(0.52)), fill=255)
    dm.polygon([(u(0.16), u(0.40)), (u(0.84), u(0.40)), (u(0.50), u(0.88))], fill=255)
    edge = ImageChops.subtract(m, m.filter(ImageFilter.MinFilter(stroke(0.042) | 1)))
    img = canvas()
    solid = Image.new("RGBA", (S, S), color)
    img.paste(solid, (0, 0), edge)
    return img


# ---------- 输出 ----------

def main():
    os.makedirs(OUT, exist_ok=True)
    print("生成图标 →", os.path.normpath(OUT))

    drawers = {
        "kitchen": draw_chef_hat_mask,
        "order": draw_receipt,
        "ledger": draw_ledger,
        "fridge": draw_fridge,
        "me": draw_person,
    }
    for name, draw in drawers.items():
        save(draw(GRAY), f"tab-{name}.png", 81)
        save(draw(ACTIVE), f"tab-{name}-active.png", 81)

    inline = {
        "chefhat.png": draw_chef_hat_mask(ACTIVE),
        "pot.png": draw_pot(GRAY),
        "person.png": draw_person(GRAY),
        "search.png": draw_search(GRAY),
        "cart.png": draw_cart(GRAY),
        "cart-active.png": draw_cart(ACTIVE),
        "qr.png": draw_qr(GRAY),
        "basket.png": draw_basket(GRAY),
        "receipt.png": draw_receipt(GRAY),
        "heart.png": draw_heart((232, 131, 111, 255)),   # 情侣粉 #E8836F
        "heart-light.png": draw_heart((247, 199, 187, 255)),
    }
    for name, img in inline.items():
        save(img, name, 96)

    save(draw_receipt(LIGHT), "empty-order.png", 192)
    save(draw_ledger(LIGHT), "empty-ledger.png", 192)
    save(draw_pot(LIGHT), "empty-kitchen.png", 192)
    print("完成")


if __name__ == "__main__":
    main()
