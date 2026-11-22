from PIL import Image, ImageDraw, ImageFont
import os

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(BASE, "app", "src", "main", "res")
TV_RES = os.path.join(BASE, "app", "src", "tv", "res", "drawable-nodpi")

AMBER = (245, 197, 24)
DARK = (14, 14, 14)
GRAY = (170, 170, 170)


def font(size, bold=True):
    candidates = [
        r"C:\Windows\Fonts\msyhbd.ttc" if bold else r"C:\Windows\Fonts\msyh.ttc",
        r"C:\Windows\Fonts\msyh.ttc",
        r"C:\Windows\Fonts\arialbd.ttf",
    ]
    for path in candidates:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except Exception:
                pass
    return ImageFont.load_default()


def make_icon(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    pad = max(2, int(size * 0.06))
    radius = int(size * 0.22)
    d.rounded_rectangle([pad, pad, size - pad, size - pad], radius=radius, fill=DARK)
    d.rounded_rectangle(
        [pad, pad, size - pad, size - pad],
        radius=radius,
        outline=AMBER,
        width=max(2, size // 32),
    )
    # 播放三角
    tri = [
        (size * 0.40, size * 0.30),
        (size * 0.40, size * 0.70),
        (size * 0.72, size * 0.50),
    ]
    d.polygon(tri, fill=AMBER)
    # 左上角小黄点，缩小后仍能识别
    r = max(2, int(size * 0.07))
    d.ellipse([pad + r, pad + r, pad + 3 * r, pad + 3 * r], fill=(230, 70, 70))
    return img


def make_banner():
    w, h = 320, 180
    img = Image.new("RGB", (w, h), DARK)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, w, h], fill=DARK)
    # 左侧图标
    icon = make_icon(96)
    img.paste(icon, (18, 42), icon)
    # 右侧文字
    d.text((128, 46), "黄果解析", font=font(34, True), fill=AMBER)
    d.text((130, 92), "短剧 · 吃瓜 · 排行榜", font=font(16, False), fill=GRAY)
    d.text((130, 118), "TV", font=font(14, True), fill=(120, 180, 255))
    return img


def main():
    sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for bucket, px in sizes.items():
        out = os.path.join(RES, "mipmap-" + bucket)
        os.makedirs(out, exist_ok=True)
        make_icon(px).save(os.path.join(out, "ic_launcher.png"))
        # 圆角图标也用它，launcher 会自行裁切
        make_icon(px).save(os.path.join(out, "ic_launcher_round.png"))
    os.makedirs(TV_RES, exist_ok=True)
    make_banner().save(os.path.join(TV_RES, "tv_banner.png"))
    print("assets generated")


if __name__ == "__main__":
    main()
