# Install banner option C (Bold orange) and matching app icons into the Jellyeye app.
import os, sys
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(__file__))
from banners import concept_c, mark, linear, BG, GOLD, W, H

ROOT = sys.argv[1]
res = os.path.join(ROOT, 'app/src/main/res')


def save(im, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if path.endswith('.webp'):
        im.save(path, 'WEBP', lossless=True)
    else:
        im.save(path, optimize=True)


# --- TV banner: plain PNGs at three densities (xhdpi is the standard 320x180) ---
banner = concept_c().convert('RGB')
for dens, size in {'xhdpi': (320, 180), 'xxhdpi': (480, 270), 'xxxhdpi': (640, 360)}.items():
    save(banner.resize(size, Image.LANCZOS), f'{res}/mipmap-{dens}/ic_banner.png')


# --- square app icon: the same orange with the dark eye ---
def orange(size):
    return linear(size, size, (247, 178, 35), (205, 110, 4), 45).convert('RGBA')


def dark_mark(size):
    return mark(size, ring=BG, iris=(BG, (40, 40, 46)), pupil=GOLD)


for dens, (leg, fg) in {'mdpi': (48, 108), 'hdpi': (72, 162), 'xhdpi': (96, 216), 'xxhdpi': (144, 324), 'xxxhdpi': (192, 432)}.items():
    # adaptive icon: orange background layer + eye inside the 66% safe zone
    save(orange(fg), f'{res}/mipmap-{dens}/ic_launcher_background.webp')
    f = Image.new('RGBA', (fg, fg), (0, 0, 0, 0))
    m = dark_mark(int(fg * 0.58))
    f.alpha_composite(m, ((fg - m.width) // 2, (fg - m.height) // 2))
    save(f, f'{res}/mipmap-{dens}/ic_launcher_foreground.webp')
    # older devices: ready-made rounded square and circle
    S = leg * 4
    base = orange(S)
    m = dark_mark(int(S * 0.78))
    base.alpha_composite(m, ((S - m.width) // 2, (S - m.height) // 2))
    for name, shape in (('ic_launcher', 'square'), ('ic_launcher_round', 'circle')):
        mask = Image.new('L', (S, S), 0)
        d = ImageDraw.Draw(mask)
        if shape == 'square':
            d.rounded_rectangle([0, 0, S - 1, S - 1], radius=S * 0.22, fill=255)
        else:
            d.ellipse([0, 0, S - 1, S - 1], fill=255)
        out = Image.new('RGBA', (S, S), (0, 0, 0, 0))
        out.paste(base, (0, 0), mask)
        save(out.resize((leg, leg), Image.LANCZOS), f'{res}/mipmap-{dens}/{name}.webp')

# Fire TV shows a sideloaded app's ordinary icon (not its banner), fitted to the height of a 16:9 tile and
# centred on grey. So the ordinary icon is made 16:9 too: fitted into the tile it then fills all of it.
for dens, size in {'mdpi': (160, 90), 'hdpi': (240, 135), 'xhdpi': (320, 180), 'xxhdpi': (480, 270), 'xxxhdpi': (640, 360)}.items():
    save(banner.resize(size, Image.LANCZOS).convert('RGBA'), f'{res}/mipmap-{dens}/ic_launcher.webp')

# previews
banner.resize((960, 540), Image.LANCZOS).save(os.path.join(os.path.dirname(__file__), 'preview-banner-C.png'))
Image.open(f'{res}/mipmap-xxxhdpi/ic_launcher.webp').resize((384, 384), Image.LANCZOS).save(os.path.join(os.path.dirname(__file__), 'preview-icon-C.png'))
print('done')
