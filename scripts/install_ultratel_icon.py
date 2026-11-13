#!/usr/bin/env python3
"""Prepare UltraTel launcher artwork for Android resources during the CI build.

Upload a square 512x512 PNG to branding/ultratel-launcher.png first. This file
is the launcher-safe symbol-only variant: no small lettering to be clipped.
The original Telegram alternate icon assets are not changed by this script.
"""
from pathlib import Path
import sys

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "branding/ultratel-launcher.png"
RES = ROOT / "TMessagesProj/src/main/res"
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def main():
    if not SOURCE.is_file():
        sys.exit(
            "UltraTel: falta branding/ultratel-launcher.png. "
            "Adicione a imagem ao PR antes de executar o workflow."
        )
    with Image.open(SOURCE) as original:
        if original.format != "PNG" or original.width != original.height:
            sys.exit("UltraTel: o icone precisa ser PNG quadrado.")
        icon = original.convert("RGBA")

    for dpi, side in DENSITIES.items():
        directory = RES / ("mipmap-" + dpi)
        directory.mkdir(parents=True, exist_ok=True)
        scaled = icon.resize((side, side), Image.Resampling.LANCZOS)
        scaled.save(directory / "ic_launcher.png", optimize=True)

        round_mask = Image.new("L", (side, side), 0)
        ImageDraw.Draw(round_mask).ellipse((0, 0, side - 1, side - 1), fill=255)
        round_icon = scaled.copy()
        round_icon.putalpha(round_mask)
        round_icon.save(directory / "ic_launcher_round.png", optimize=True)

    adaptive_path = RES / "drawable-nodpi/ultratel_adaptive_foreground.png"
    adaptive_path.parent.mkdir(parents=True, exist_ok=True)
    icon.resize((432, 432), Image.Resampling.LANCZOS).save(adaptive_path, optimize=True)

    print("UltraTel: launcher icons ready for 5 densities and adaptive Android.")


if __name__ == "__main__":
    main()
