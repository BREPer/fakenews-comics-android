"""把一批截图拼成对照表（每张缩到固定宽），便于一眼看完一轮巡检。

    python tools/android_sheet.py out.png a.png b.png c.png ...
    python tools/android_sheet.py out.png --cols 3 --w 360 shots/*.png
"""
from __future__ import annotations

import glob
import os
import sys

from PIL import Image, ImageDraw


def build(out: str, paths: list[str], cols: int = 3, tw: int = 360) -> None:
    imgs = []
    for p in paths:
        im = Image.open(p).convert("RGB")
        th = round(im.height * tw / im.width)
        imgs.append((os.path.basename(p), im.resize((tw, th), Image.LANCZOS)))
    if not imgs:
        raise SystemExit("no images")
    cell_h = max(im.height for _, im in imgs) + 22
    rows = (len(imgs) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * (tw + 8) + 8, rows * cell_h + 8), (24, 26, 32))
    d = ImageDraw.Draw(sheet)
    for i, (name, im) in enumerate(imgs):
        r, c = divmod(i, cols)
        x = 8 + c * (tw + 8)
        y = 8 + r * cell_h
        d.text((x + 2, y + 4), name, fill=(200, 200, 200))
        sheet.paste(im, (x, y + 18))
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    sheet.save(out)
    print(f"saved {out} ({len(imgs)} imgs, {sheet.width}x{sheet.height})")


if __name__ == "__main__":
    args = [a for a in sys.argv[1:]]
    if len(args) < 2:
        raise SystemExit(__doc__)
    out = args[0]
    rest = args[1:]
    cols, tw = 3, 360
    if "--cols" in rest:
        i = rest.index("--cols")
        cols = int(rest[i + 1])
        del rest[i : i + 2]
    if "--w" in rest:
        i = rest.index("--w")
        tw = int(rest[i + 1])
        del rest[i : i + 2]
    paths: list[str] = []
    for a in rest:
        paths.extend(sorted(glob.glob(a)) if any(ch in a for ch in "*?") else [a])
    build(out, paths, cols, tw)
