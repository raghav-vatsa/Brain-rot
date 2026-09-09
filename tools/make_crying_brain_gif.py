#!/usr/bin/env python3
"""Generates app/src/main/res/raw/crying_brain.gif.

The overlay shows an animated crying brain. Rather than shipping a binary blob
nobody can edit, the animation is drawn here and the GIF is regenerated with:

    pip install Pillow && python3 tools/make_crying_brain_gif.py

Frames are drawn on a supersampled canvas and downscaled, which is what gives
the shapes smooth edges. The GIF is opaque; BG matches the overlay card colour
(see res/values/colors.xml -> overlay_card) so the artwork blends into it.
"""

import math
import os

from PIL import Image, ImageChops, ImageDraw

SIZE = 240          # exported frame size in px
SS = 3              # supersampling factor
FRAMES = 24
FRAME_MS = 70

BG = (22, 18, 43)             # #16122B, must match colors.xml overlay_card
BRAIN = (247, 160, 196)       # pink
BRAIN_DARK = (198, 96, 143)   # fold / outline pink
OUTLINE = (60, 32, 62)
WHITE = (255, 253, 250)
TEAR = (109, 197, 255)
TEAR_DARK = (58, 152, 226)
MOUTH = (74, 36, 66)


def px(v):
    return v * SS


def circle(draw, cx, cy, r, fill):
    draw.ellipse((px(cx - r), px(cy - r), px(cx + r), px(cy + r)), fill=fill)


def bumps(cx, cy):
    """Circles whose union forms the bumpy brain silhouette."""
    out = []
    for i in range(14):
        a = 2 * math.pi * i / 14
        rx, ry = 62.0, 46.0
        r = 21 + 4 * math.sin(i * 2.1) + 3 * math.cos(i * 1.3)
        out.append((cx + rx * math.cos(a), cy + ry * math.sin(a), r))
    return out


def draw_brain_shape(draw, cx, cy, grow, color):
    for bx, by, r in bumps(cx, cy):
        circle(draw, bx, by, r + grow, color)
    draw.ellipse(
        (px(cx - 66 - grow), px(cy - 50 - grow), px(cx + 66 + grow), px(cy + 50 + grow)),
        fill=color,
    )


def brain_mask(cx, cy, shrink=0.0):
    mask = Image.new("L", (SIZE * SS, SIZE * SS), 0)
    draw_brain_shape(ImageDraw.Draw(mask), cx, cy, -shrink, 255)
    return mask


def fold_mask(cx, cy, t):
    """Squiggly cortex lines, as a mask clipped to the inside of the silhouette."""
    lines = Image.new("L", (SIZE * SS, SIZE * SS), 0)
    d = ImageDraw.Draw(lines)
    w = int(px(3.0))

    # central fissure
    pts = []
    for i in range(41):
        y = cy - 56 + i * (112 / 40.0)
        pts.append((px(cx + 3.5 * math.sin(y * 0.13 + t)), px(y)))
    d.line(pts, fill=255, width=w, joint="curve")

    for side in (-1, 1):
        for k in range(5):
            pts = []
            y0 = cy - 46 + k * 24
            for i in range(31):
                f = i / 30.0
                x = cx + side * (9 + f * 62)
                y = y0 + 6.0 * math.sin(f * 6.0 + k * 1.7 + t)
                pts.append((px(x), px(y)))
            d.line(pts, fill=255, width=w, joint="curve")

    inner = brain_mask(cx, cy, shrink=6.0)
    return ImageChops.multiply(lines, inner)


def teardrop(draw, x, y, rw, rh, fill):
    """Round-bottomed drop with a pointed top."""
    draw.ellipse((px(x - rw), px(y - rh * 0.55), px(x + rw), px(y + rh)), fill=fill)
    draw.polygon(
        [(px(x - rw * 0.92), px(y)), (px(x + rw * 0.92), px(y)), (px(x), px(y - rh * 1.9))],
        fill=fill,
    )


def draw_frame(idx, bg=BG, silhouette=False, out_size=None):
    """One frame. With silhouette=True every shape is drawn white on black,
    which downscales into the alpha channel of the transparent PNG."""

    def c(color):
        return (255, 255, 255) if silhouette else color

    t = 2 * math.pi * idx / FRAMES
    img = Image.new("RGB", (SIZE * SS, SIZE * SS), bg)
    d = ImageDraw.Draw(img)

    cx = SIZE / 2.0
    cy = 100 + 3.0 * math.sin(t)          # gentle sad bobbing

    # puddle of tears, rippling
    pw = 58 + 5 * math.sin(t * 2)
    d.ellipse((px(cx - pw), px(212), px(cx + pw), px(228)), fill=c(TEAR_DARK))
    d.ellipse((px(cx - pw + 9), px(213), px(cx + pw - 9), px(224)), fill=c(TEAR))

    # falling droplets (three per eye, staggered) - behind the brain body
    for side in (-1, 1):
        for k in range(3):
            p = ((idx / float(FRAMES)) + k / 3.0) % 1.0
            y = cy + 76 + p * 56
            s_ = 1.0 - 0.4 * p
            if y < 214:
                teardrop(d, cx + side * (26 + p * 10), y, 5.5 * s_, 7.0 * s_, c(TEAR))

    draw_brain_shape(d, cx, cy, 3.0, c(OUTLINE))
    draw_brain_shape(d, cx, cy, 0.0, c(BRAIN))
    if not silhouette:
        img.paste(Image.new("RGB", img.size, BRAIN_DARK), (0, 0), fold_mask(cx, cy, t))
    d = ImageDraw.Draw(img)

    for side in (-1, 1):
        ex, ey = cx + side * 26, cy - 2

        # tear stream running off the chin, drawn under the eye
        d.rounded_rectangle(
            (px(ex + side * 8 - 4), px(ey + 6), px(ex + side * 8 + 4), px(cy + 78)),
            radius=px(4),
            fill=c(TEAR),
        )

        # eye
        d.ellipse((px(ex - 16), px(ey - 17), px(ex + 16), px(ey + 17)), fill=c(OUTLINE))
        d.ellipse((px(ex - 13.5), px(ey - 14.5), px(ex + 13.5), px(ey + 14.5)), fill=c(WHITE))
        py_ = ey + 4 + 1.2 * math.sin(t)
        d.ellipse((px(ex - 7), px(py_ - 7), px(ex + 7), px(py_ + 7)), fill=c(OUTLINE))
        d.ellipse((px(ex - 6), px(py_ - 5.5), px(ex - 1.5), px(py_ - 1)), fill=c(WHITE))

        # tear welling up at the corner of the eye
        teardrop(d, ex + side * 8, ey + 14 + 2 * math.sin(t + side), 6.5, 8.5, c(TEAR))

        # sad eyebrow: inner end raised, outer end dropped
        d.line(
            [(px(ex - side * 13), px(ey - 30)), (px(ex + side * 15), px(ey - 22))],
            fill=c(OUTLINE),
            width=int(px(4.5)),
        )

    # frown
    d.arc(
        (px(cx - 19), px(cy + 22), px(cx + 19), px(cy + 46)),
        start=200,
        end=340,
        fill=c(MOUTH),
        width=int(px(4.5)),
    )

    side = out_size or SIZE
    return img.resize((side, side), Image.LANCZOS)


ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Both platforms are fed from this one script.
GIF_TARGETS = [
    os.path.join(ROOT, "android", "app", "src", "main", "res", "raw", "crying_brain.gif"),
    os.path.join(ROOT, "ios", "BrainRot", "Resources", "crying_brain.gif"),
    os.path.join(ROOT, "ios", "BrainRotMonitor", "Resources", "crying_brain.gif"),
]

# iOS shields take a single still image, not an animation, so the shield gets a
# transparent PNG of one frame instead.
PNG_TARGETS = [
    os.path.join(ROOT, "ios", "BrainRotShield", "Resources", "crying_brain.png"),
]
# iOS app icon: one opaque frame, rendered large.
ICON_TARGET = os.path.join(
    ROOT, "ios", "BrainRot", "Resources", "Assets.xcassets", "AppIcon.appiconset", "icon_1024.png"
)
PNG_FRAME = 6


def transparent_frame(idx):
    """One frame with real alpha. Drawn over the outline colour rather than
    over nothing, so the edges downscale into the outline instead of into a
    dark halo."""
    art = draw_frame(idx, bg=OUTLINE).convert("RGBA")
    alpha = draw_frame(idx, bg=(0, 0, 0), silhouette=True).convert("L")
    art.putalpha(alpha)
    return art


def app_icon(idx, size=1024, supersample=8):
    """App icons must be opaque and large, so this re-renders one frame on a
    bigger canvas instead of upscaling the 240px artwork."""
    global SS
    original = SS
    SS = supersample
    try:
        return draw_frame(idx, out_size=size)
    finally:
        SS = original


def write(path, save):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    save(path)
    print("wrote", path, os.path.getsize(path), "bytes")


def main():
    frames = [draw_frame(i) for i in range(FRAMES)]
    master = frames[0].quantize(colors=128, method=Image.MEDIANCUT)
    frames = [f.quantize(palette=master, dither=Image.NONE) for f in frames]

    for path in GIF_TARGETS:
        write(path, lambda p: frames[0].save(
            p,
            save_all=True,
            append_images=frames[1:],
            duration=FRAME_MS,
            loop=0,
            optimize=True,
        ))

    still = transparent_frame(PNG_FRAME)
    for path in PNG_TARGETS:
        write(path, lambda p: still.save(p))

    write(ICON_TARGET, lambda p: app_icon(PNG_FRAME).save(p))


if __name__ == "__main__":
    main()
