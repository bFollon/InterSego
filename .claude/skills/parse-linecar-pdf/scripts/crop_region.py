#!/usr/bin/env python3
"""Crop and zoom a region of a rendered PDF page for close reading.

The rendered PNGs from render_pdf.sh are full-page and too dense to read
reliably at native resolution — footnote symbols and shading are easy to
misread (this caused a real bug on M4). Crop tightly to one table or one
footnote block at a time, and zoom 1.5-3x, before transcribing anything.

Usage:
    python3 crop_region.py <input.png> <output.png> \
        --left 0.0 --top 0.2 --right 1.0 --bottom 0.6 --zoom 2.0

Coordinates are fractions of image width/height (0.0-1.0), not pixels —
this makes it easy to eyeball "roughly the left half, upper-middle third"
without knowing the exact pixel dimensions up front. Re-run with tighter
bounds if the result is still ambiguous; don't guess from a loose crop.
"""
import argparse
from PIL import Image


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("input", help="Path to the source PNG (e.g. m1.pdf.png)")
    parser.add_argument("output", help="Path to write the cropped/zoomed PNG")
    parser.add_argument("--left", type=float, default=0.0, help="Left bound, fraction of width [0-1]")
    parser.add_argument("--top", type=float, default=0.0, help="Top bound, fraction of height [0-1]")
    parser.add_argument("--right", type=float, default=1.0, help="Right bound, fraction of width [0-1]")
    parser.add_argument("--bottom", type=float, default=1.0, help="Bottom bound, fraction of height [0-1]")
    parser.add_argument("--zoom", type=float, default=2.0, help="Zoom multiplier applied after cropping")
    args = parser.parse_args()

    im = Image.open(args.input)
    w, h = im.size
    box = (
        int(w * args.left),
        int(h * args.top),
        int(w * args.right),
        int(h * args.bottom),
    )
    crop = im.crop(box)
    if args.zoom != 1.0:
        crop = crop.resize((int(crop.width * args.zoom), int(crop.height * args.zoom)))
    crop.save(args.output)
    print(f"Saved {args.output} ({crop.width}x{crop.height}, from box {box} of {w}x{h} source)")


if __name__ == "__main__":
    main()
