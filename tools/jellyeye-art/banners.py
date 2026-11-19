# Jellyeye TV banner concepts (Fire TV "Your Apps" tile is 320x180; drawn at 4x then reduced)
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageChops
import math, random, os

OUT = '/home/claude/jellyeye-art/concepts'
os.makedirs(OUT, exist_ok=True)
W, H = 1280, 720
INTER = '/usr/share/fonts/opentype/inter/'
BG = (17, 18, 21)
ORANGE = (229, 160, 13)
GOLD = (247, 190, 52)
DEEP = (176, 104, 6)


def font(name, size):
    return ImageFont.truetype(INTER + name, size)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def linear(w, h, c1, c2, angle=0):
    """gradient left->right (angle 0) or top->bottom (angle 90)"""
    g = Image.new('RGB', (w, h))
    px = g.load()
    for y in range(h):
        for x in range(w):
            t = (x / (w - 1)) if angle == 0 else (y / (h - 1)) if angle == 90 else ((x / w) * 0.6 + (y / h) * 0.4)
            px[x, y] = lerp(c1, c2, t)
    return g


def radial(w, h, cx, cy, r, inner, outer):
    g = Image.new('RGB', (w // 4, h // 4))
    px = g.load()
    for y in range(h // 4):
        for x in range(w // 4):
            d = math.hypot(x * 4 - cx, y * 4 - cy) / r
            px[x, y] = lerp(inner, outer, min(1, d) ** 1.4)
    return g.resize((w, h), Image.BICUBIC)


def almond(cx, cy, w, h, n=240):
    pts = []
    for i in range(n + 1):
        a = math.pi * i / n
        pts.append((cx - w / 2 + w * i / n, cy - (math.sin(a) ** 0.8) * h / 2))
    for i in range(n, -1, -1):
        a = math.pi * i / n
        pts.append((cx - w / 2 + w * i / n, cy + (math.sin(a) ** 0.8) * h / 2))
    return pts


def mark(size, ring=ORANGE, iris=(GOLD, ORANGE), pupil=BG, outline_only=False):
    """The Jellyeye mark: an almond eye whose iris holds a rounded play button. Transparent background."""
    S = size * 4
    im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    cx, cy = S / 2, S / 2
    w, h = S * 0.96, S * 0.58
    t = S * 0.075
    eye = Image.new('L', (S, S), 0)
    ImageDraw.Draw(eye).polygon(almond(cx, cy, w, h), fill=255)
    hole = Image.new('L', (S, S), 0)
    ImageDraw.Draw(hole).polygon(almond(cx, cy, w - t * 2.6, h - t * 2.0), fill=255)
    ringmask = ImageChops.subtract(eye, hole)
    im.paste(Image.new('RGBA', (S, S), ring + (255,)), (0, 0), ringmask)
    # iris with a soft top-to-bottom gradient
    r = h * 0.36
    irisimg = linear(int(2 * r), int(2 * r), iris[0], iris[1], 90).convert('RGBA')
    m = Image.new('L', irisimg.size, 0)
    ImageDraw.Draw(m).ellipse([0, 0, irisimg.size[0] - 1, irisimg.size[1] - 1], fill=255)
    im.paste(irisimg, (int(cx - r), int(cy - r)), m)
    # rounded play triangle
    p = r * 0.52
    tri = Image.new('L', (S, S), 0)
    td = ImageDraw.Draw(tri)
    pts = [(cx - p * 0.42, cy - p * 0.66), (cx - p * 0.42, cy + p * 0.66), (cx + p * 0.72, cy)]
    td.polygon(pts, fill=255)
    tri = tri.filter(ImageFilter.GaussianBlur(S * 0.006)).point(lambda v: 255 if v > 110 else 0)
    im.paste(Image.new('RGBA', (S, S), pupil + (255,)), (0, 0), tri)
    # small highlight on the iris
    hl = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(hl).ellipse([cx + r * 0.28, cy - r * 0.62, cx + r * 0.52, cy - r * 0.38], fill=(255, 255, 255, 120))
    im.alpha_composite(hl.filter(ImageFilter.GaussianBlur(S * 0.004)))
    return im.resize((size, size), Image.LANCZOS)


def text(im, xy, s, f, fill, anchor='lm', shadow=None):
    d = ImageDraw.Draw(im)
    if shadow:
        sh = Image.new('RGBA', im.size, (0, 0, 0, 0))
        ImageDraw.Draw(sh).text((xy[0], xy[1] + 6), s, font=f, fill=shadow, anchor=anchor)
        im.alpha_composite(sh.filter(ImageFilter.GaussianBlur(10)))
        d = ImageDraw.Draw(im)
    d.text(xy, s, font=f, fill=fill, anchor=anchor)


def lockup(im, mark_img, word, f, color, cy, gap=34, shadow=None):
    """mark + wordmark centred as one group"""
    d = ImageDraw.Draw(im)
    tw = d.textlength(word, font=f)
    total = mark_img.width + gap + tw
    x0 = (W - total) / 2
    im.alpha_composite(mark_img, (int(x0), int(cy - mark_img.height / 2)))
    text(im, (x0 + mark_img.width + gap, cy + 4), word, f, color, 'lm', shadow)


def vignette(im, strength=170):
    v = Image.new('L', (W, H), 0)
    ImageDraw.Draw(v).rectangle([0, 0, W, H], fill=strength)
    hole = Image.new('L', (W, H), 0)
    ImageDraw.Draw(hole).ellipse([-W * 0.15, -H * 0.3, W * 1.15, H * 1.3], fill=255)
    v = ImageChops.subtract(v, hole).filter(ImageFilter.GaussianBlur(120))
    im.alpha_composite(Image.merge('RGBA', [Image.new('L', (W, H), 0)] * 3 + [v]))


# ---------- A: Cinema glow ----------
def concept_a():
    im = radial(W, H, W * 0.5, H * 0.52, W * 0.62, (70, 44, 6), BG).convert('RGBA')
    # faint light rays from behind the mark
    rays = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    rd = ImageDraw.Draw(rays)
    for i in range(14):
        a = math.radians(-90 + (i - 6.5) * 13)
        rd.polygon([(W * 0.5, H * 0.5), (W * 0.5 + math.cos(a - 0.05) * 1400, H * 0.5 + math.sin(a - 0.05) * 1400),
                    (W * 0.5 + math.cos(a + 0.05) * 1400, H * 0.5 + math.sin(a + 0.05) * 1400)], fill=(255, 190, 70, 14))
    im.alpha_composite(rays.filter(ImageFilter.GaussianBlur(18)))
    vignette(im)
    m = mark(250)
    glow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    glow.alpha_composite(m, (int(W / 2 - 125), int(H * 0.40 - 125)))
    glow = glow.filter(ImageFilter.GaussianBlur(40))
    im.alpha_composite(glow)
    im.alpha_composite(m, (int(W / 2 - 125), int(H * 0.40 - 125)))
    text(im, (W / 2, H * 0.74), 'Jellyeye', font('InterDisplay-ExtraBold.otf', 170), (255, 255, 255), 'mm', (0, 0, 0, 200))
    return im


# ---------- B: Poster wall ----------
def concept_b():
    random.seed(7)
    im = Image.new('RGBA', (W, H), BG + (255,))
    wall = Image.new('RGBA', (W * 2, H * 2), (0, 0, 0, 0))
    palette = [(120, 40, 50), (30, 70, 120), (200, 120, 30), (40, 100, 80), (90, 50, 120), (150, 70, 30), (30, 40, 70), (170, 150, 60), (80, 30, 30), (30, 110, 140)]
    pw, ph, g = 170, 255, 22
    for row in range(-1, 8):
        for col in range(-1, 14):
            x = col * (pw + g) + (row % 2) * 60
            y = row * (ph + g)
            c1 = random.choice(palette)
            c2 = lerp(c1, (10, 10, 12), 0.65)
            tile = linear(pw, ph, c1, c2, 90).convert('RGBA')
            # suggestion of a title band at the bottom of each "poster"
            td = ImageDraw.Draw(tile)
            td.rectangle([14, ph - 52, pw - 14 - random.randint(0, 60), ph - 40], fill=(255, 255, 255, 70))
            mk = Image.new('L', (pw, ph), 0)
            ImageDraw.Draw(mk).rounded_rectangle([0, 0, pw - 1, ph - 1], radius=18, fill=255)
            wall.paste(tile, (x, y), mk)
    wall = wall.rotate(-12, resample=Image.BICUBIC, center=(W, H))
    wall = wall.crop((W // 2, H // 2, W // 2 + W, H // 2 + H)).filter(ImageFilter.GaussianBlur(3))
    im.alpha_composite(wall)
    # darken towards the middle so the logo reads, keep colour at the edges
    shade = radial(W, H, W * 0.5, H * 0.5, W * 0.55, (8, 8, 10), (8, 8, 10))
    a = radial(W, H, W * 0.5, H * 0.5, W * 0.62, (235, 235, 235), (120, 120, 120)).convert('L')
    im.alpha_composite(Image.merge('RGBA', (*shade.split(), a)))
    lockup(im, mark(230), 'Jellyeye', font('InterDisplay-ExtraBold.otf', 158), (255, 255, 255), H / 2, 30, (0, 0, 0, 220))
    # thin orange "progress" line, like the spotlight tabs
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([W * 0.30, H * 0.72, W * 0.70, H * 0.72 + 10], radius=5, fill=(255, 255, 255, 40))
    d.rounded_rectangle([W * 0.30, H * 0.72, W * 0.55, H * 0.72 + 10], radius=5, fill=ORANGE + (255,))
    return im


# ---------- C: Bold orange ----------
def concept_c():
    im = linear(W, H, (247, 178, 35), (205, 110, 4), 45).convert('RGBA')
    # big faint eye in the background for depth
    big = mark(1500, ring=(255, 215, 120), iris=((255, 210, 110), (255, 200, 90)), pupil=(240, 160, 30))
    big.putalpha(big.getchannel('A').point(lambda v: v * 0.22))
    im.alpha_composite(big, (int(W * 0.42), int(-H * 0.55)))
    lockup(im, mark(230, ring=BG, iris=(BG, (40, 40, 46)), pupil=GOLD), 'Jellyeye',
           font('InterDisplay-Black.otf', 166), BG, H / 2, 30)
    return im


# ---------- D: Spotlight card ----------
def concept_d():
    im = linear(W, H, (28, 29, 34), (12, 12, 14), 90).convert('RGBA')
    beam = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(beam).polygon([(W * 0.42, -20), (W * 0.58, -20), (W * 0.86, H), (W * 0.14, H)], fill=(255, 196, 80, 46))
    im.alpha_composite(beam.filter(ImageFilter.GaussianBlur(60)))
    floor = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(floor).ellipse([W * 0.18, H * 0.78, W * 0.82, H * 1.08], fill=(229, 160, 13, 70))
    im.alpha_composite(floor.filter(ImageFilter.GaussianBlur(50)))
    m = mark(210)
    im.alpha_composite(m, (int(W / 2 - 105), int(H * 0.30 - 105)))
    text(im, (W / 2, H * 0.62), 'JELLYEYE', font('InterDisplay-ExtraBold.otf', 142), (255, 255, 255), 'mm', (0, 0, 0, 200))
    # letter-spaced look: redraw with tracking
    return im


def concept_d_tracked():
    im = concept_d_base()
    f = font('InterDisplay-ExtraBold.otf', 132)
    word = 'JELLYEYE'
    d = ImageDraw.Draw(im)
    track = 26
    widths = [d.textlength(ch, font=f) for ch in word]
    total = sum(widths) + track * (len(word) - 1)
    x = (W - total) / 2
    sh = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    sd = ImageDraw.Draw(sh)
    for ch, w in zip(word, widths):
        sd.text((x, H * 0.66 + 6), ch, font=f, fill=(0, 0, 0, 200), anchor='lm')
        x += w + track
    im.alpha_composite(sh.filter(ImageFilter.GaussianBlur(10)))
    x = (W - total) / 2
    d = ImageDraw.Draw(im)
    for ch, w in zip(word, widths):
        d.text((x, H * 0.66), ch, font=f, fill=(255, 255, 255), anchor='lm')
        x += w + track
    d.rounded_rectangle([W / 2 - 70, H * 0.80, W / 2 + 70, H * 0.80 + 9], radius=5, fill=ORANGE + (255,))
    return im


def concept_d_base():
    im = linear(W, H, (30, 31, 36), (11, 11, 13), 90).convert('RGBA')
    beam = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(beam).polygon([(W * 0.44, -20), (W * 0.56, -20), (W * 0.84, H), (W * 0.16, H)], fill=(255, 196, 80, 40))
    im.alpha_composite(beam.filter(ImageFilter.GaussianBlur(60)))
    m = mark(230)
    glow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    glow.alpha_composite(m, (int(W / 2 - 115), int(H * 0.31 - 115)))
    im.alpha_composite(glow.filter(ImageFilter.GaussianBlur(34)))
    im.alpha_composite(m, (int(W / 2 - 115), int(H * 0.31 - 115)))
    return im


def finish(im, radius=0):
    return im.convert('RGB').resize((320, 180), Image.LANCZOS)


if __name__ == '__main__':
    concepts = {
        'A': ('Cinema glow', concept_a()),
        'B': ('Poster wall', concept_b()),
        'C': ('Bold orange', concept_c()),
        'D': ('Spotlight', concept_d_tracked()),
    }
    for k, (name, im) in concepts.items():
        im.convert('RGB').save(f'{OUT}/{k}-full.png')
        finish(im).save(f'{OUT}/{k}-320x180.png')


    # ---------- Fire TV "Your Apps" mock-ups ----------
    def fire_row(focus_key):
        SW, SH = 1920, 1080
        scr = Image.new('RGBA', (SW, SH), (14, 16, 22, 255))
        scr.alpha_composite(radial(SW, SH, SW * 0.2, -100, SW * 0.9, (34, 44, 64), (14, 16, 22)).convert('RGBA'))
        d = ImageDraw.Draw(scr)
        d.text((110, 120), 'Your Apps', font=font('Inter-SemiBold.otf', 46), fill=(235, 238, 242))
        d.text((110, 182), 'Jellyeye banner options', font=font('Inter-SemiBold.otf', 26), fill=(140, 146, 156))
        tw, th, gap = 400, 225, 38
        # neutral stand-in tiles for other apps (no real logos)
        others = [((60, 70, 90), 'Settings'), ((40, 40, 46), 'Files'), ((70, 30, 34), 'Video'), ((26, 60, 52), 'Music'), ((48, 42, 80), 'Photos')]
        keys = list(concepts.keys())
        y0 = 300
        for row, k in enumerate(keys):
            y = y0 + row * 0  # single row per image
        tiles = []
        order = [None, focus_key, None, None]
        x = 110
        i_other = 0
        cy = 520
        for slot in range(4):
            if slot == 1:
                img = concepts[focus_key][1].convert('RGB').resize((int(tw * 1.12), int(th * 1.12)), Image.LANCZOS)
                fx, fy = x - int(tw * 0.06), cy - int(th * 0.56)
                sh = Image.new('RGBA', (SW, SH), (0, 0, 0, 0))
                ImageDraw.Draw(sh).rounded_rectangle([fx + 6, fy + 16, fx + img.width + 6, fy + img.height + 22], radius=16, fill=(0, 0, 0, 170))
                scr.alpha_composite(sh.filter(ImageFilter.GaussianBlur(18)))
                m = Image.new('L', img.size, 0)
                ImageDraw.Draw(m).rounded_rectangle([0, 0, img.width - 1, img.height - 1], radius=14, fill=255)
                scr.paste(img, (fx, fy), m)
                ImageDraw.Draw(scr).rounded_rectangle([fx - 7, fy - 7, fx + img.width + 6, fy + img.height + 6], radius=20, outline=(255, 255, 255), width=6)
                ImageDraw.Draw(scr).text((fx, fy + img.height + 30), 'Jellyeye', font=font('Inter-SemiBold.otf', 30), fill=(235, 238, 242))
            else:
                col, label = others[i_other % len(others)]
                i_other += 1
                fx, fy = x, cy - th // 2
                t = linear(tw, th, lerp(col, (255, 255, 255), 0.08), col, 90).convert('RGBA')
                td = ImageDraw.Draw(t)
                td.rounded_rectangle([tw / 2 - 34, th / 2 - 46, tw / 2 + 34, th / 2 + 22], radius=16, fill=(255, 255, 255, 60))
                td.text((tw / 2, th / 2 + 58), label, font=font('Inter-SemiBold.otf', 30), fill=(255, 255, 255, 170), anchor='mm')
                m = Image.new('L', (tw, th), 0)
                ImageDraw.Draw(m).rounded_rectangle([0, 0, tw - 1, th - 1], radius=14, fill=255)
                scr.paste(t, (fx, fy), m)
            x += tw + gap + (int(tw * 0.06) if slot == 1 else 0)
        d = ImageDraw.Draw(scr)
        d.text((110, 840), f'Option {focus_key}: {concepts[focus_key][0]}', font=font('Inter-Bold.otf', 44), fill=(255, 255, 255))
        return scr.convert('RGB')


    for k in concepts:
        fire_row(k).resize((1280, 720), Image.LANCZOS).save(f'{OUT}/firetv-{k}.png')

    # one sheet with all four at actual banner proportions
    sheet = Image.new('RGB', (1400, 880), (14, 16, 22))
    sd = ImageDraw.Draw(sheet)
    for i, (k, (name, im)) in enumerate(concepts.items()):
        cx = 40 + (i % 2) * 680
        cy = 40 + (i // 2) * 420
        b = im.convert('RGB').resize((640, 360), Image.LANCZOS)
        m = Image.new('L', b.size, 0)
        ImageDraw.Draw(m).rounded_rectangle([0, 0, 639, 359], radius=18, fill=255)
        sheet.paste(b, (cx, cy), m)
        sd.text((cx, cy + 372), f'{k} · {name}', font=font('Inter-Bold.otf', 26), fill=(235, 238, 242))
    sheet.save(f'{OUT}/all-options.png')
    print('ok')
