#!/usr/bin/env python3
"""앱 아이콘 생성 (금색 둥근 사각형 + 픽셀 글자 'SB'). 결과: desktopApp/icons/*, 트레이 아이콘, Android 런처 아이콘."""
import os
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..")
# 5x7 픽셀 글꼴
GLYPHS = {
    "S": ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
    "B": ["11110", "10001", "10001", "11110", "10001", "10001", "11110"],
}


def icon(size):
    scale = 4
    big = size * scale
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    r = big * 0.22
    # 세로 그라데이션 금색
    grad = Image.new("RGBA", (big, big))
    gd = ImageDraw.Draw(grad)
    for y in range(big):
        t = y / big
        c = (int(255 - 31 * t), int(194 - 71 * t), int(61 - 61 * t), 255)
        gd.line([(0, y), (big, y)], fill=c)
    mask = Image.new("L", (big, big), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, big - 1, big - 1], radius=r, fill=255)
    img.paste(grad, (0, 0), mask)
    d.rounded_rectangle([big * 0.04, big * 0.04, big * 0.96, big * 0.96], radius=r * 0.85,
                        outline=(255, 255, 255, 70), width=max(2, big // 64))
    # 글자
    px = big / 15
    text_w = (5 + 1 + 5) * px
    x0 = (big - text_w) / 2
    y0 = (big - 7 * px) / 2
    for i, ch in enumerate("SB"):
        for row, bits in enumerate(GLYPHS[ch]):
            for col, b in enumerate(bits):
                if b == "1":
                    x = x0 + (i * 6 + col) * px
                    y = y0 + row * px
                    d.rectangle([x + px * 0.12, y + px * 0.12, x + px * 1.12, y + px * 1.12], fill=(60, 30, 0, 110))
                    d.rectangle([x, y, x + px - 1, y + px - 1], fill=(36, 21, 0, 255))
    return img.resize((size, size), Image.LANCZOS)


def main():
    icons = os.path.join(ROOT, "desktopApp", "icons")
    os.makedirs(icons, exist_ok=True)
    base = icon(512)
    base.save(os.path.join(icons, "app.png"))
    base.save(os.path.join(icons, "app.ico"), sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    base.save(os.path.join(icons, "app.icns"))
    res = os.path.join(ROOT, "desktopApp", "src", "main", "resources")
    os.makedirs(res, exist_ok=True)
    icon(64).save(os.path.join(res, "tray.png"))
    icon(256).save(os.path.join(res, "app.png"))
    for density, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        d = os.path.join(ROOT, "androidApp", "src", "main", "res", f"mipmap-{density}")
        os.makedirs(d, exist_ok=True)
        icon(px).save(os.path.join(d, "ic_launcher.png"))
    print("icons written")


if __name__ == "__main__":
    main()
