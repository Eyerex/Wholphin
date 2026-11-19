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

# Fire TV shows a sideloaded app's ordinary icon as a square at the tile's height, on grey. Like SmartTube,
# the icon is a see-through square holding a rounded orange card (eye + name), so it sits neatly on the grey.
from PIL import ImageFilter, ImageFont


def tile_icon(size):
    S = size * 4
    im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    cw, ch = int(S * 0.92), int(S * 0.60)
    x0, y0 = (S - cw) // 2, (S - ch) // 2 - int(S * 0.02)
    # soft shadow under the card
    sh = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle([x0, y0 + S * 0.025, x0 + cw, y0 + ch + S * 0.025], radius=S * 0.09, fill=(0, 0, 0, 90))
    im.alpha_composite(sh.filter(ImageFilter.GaussianBlur(S * 0.02)))
    card = linear(cw, ch, (247, 178, 35), (205, 110, 4), 45).convert('RGBA')
    m = Image.new('L', (cw, ch), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, cw - 1, ch - 1], radius=S * 0.09, fill=255)
    im.paste(card, (x0, y0), m)
    # eye above the name
    e = dark_mark(int(ch * 0.62))
    im.alpha_composite(e, (S // 2 - e.width // 2, y0 + int(ch * 0.02)))
    f = ImageFont.truetype('/usr/share/fonts/opentype/inter/InterDisplay-Black.otf', int(ch * 0.25))
    ImageDraw.Draw(im).text((S / 2, y0 + ch * 0.78), 'Jellyeye', font=f, fill=BG + (255,), anchor='mm')
    return im.resize((size, size), Image.LANCZOS)


# drawn larger than the usual launcher sizes so it stays sharp when Fire TV scales it up
for dens, size in {'mdpi': 128, 'hdpi': 192, 'xhdpi': 256, 'xxhdpi': 384, 'xxxhdpi': 512}.items():
    save(tile_icon(size), f'{res}/mipmap-{dens}/ic_launcher.webp')

# preview: how it sits in a grey Fire TV tile
pv = Image.new('RGBA', (1000, 560), (14, 16, 22, 255))
g = Image.new('RGBA', (880, 495), (176, 180, 190, 255))
gm = Image.new('L', g.size, 0)
ImageDraw.Draw(gm).rounded_rectangle([0, 0, 879, 494], radius=22, fill=255)
pv.paste(g, (60, 32), gm)
ic = tile_icon(495)
pv.alpha_composite(ic, (60 + (880 - 495) // 2, 32))
pv.convert('RGB').save(os.path.join(os.path.dirname(__file__), 'preview-firetv-tile.png'))

# previews
banner.resize((960, 540), Image.LANCZOS).save(os.path.join(os.path.dirname(__file__), 'preview-banner-C.png'))
Image.open(f'{res}/mipmap-xxxhdpi/ic_launcher.webp').resize((384, 384), Image.LANCZOS).save(os.path.join(os.path.dirname(__file__), 'preview-icon-C.png'))
print('done')
