"""Export generated MoonCat v2 art to the app's existing Android resources."""
from pathlib import Path
from PIL import Image, ImageOps, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'branding/v2'
RES = ROOT / 'app/src/main/res'

def export(source, dest, size=None, crop=False):
    image = Image.open(ART / source).convert('RGBA')
    size = size or Image.open(dest).size
    if crop:
        image = ImageOps.fit(image, size, Image.Resampling.LANCZOS)
    else:
        image = ImageOps.pad(image, size, Image.Resampling.LANCZOS, color=(0, 0, 0, 0))
    dest.parent.mkdir(parents=True, exist_ok=True)
    image.save(dest, **({'quality': 88, 'method': 6} if dest.suffix == '.webp' else {'optimize': True}))

for path in RES.glob('mipmap-*/ic_launcher*.png'):
    export('mascot.webp', path)
for path in RES.glob('mipmap-*/banner*.png'):
    export('scene.webp', path, crop=True)
for folder in ('drawable', 'drawable-nodpi'):
    path = RES / folder / 'tv_banner.png'
    if path.exists(): export('scene.webp', path, crop=True)
for path in (RES / 'drawable/app_logo_mark.png', RES / 'drawable/ic_launcher.png', ROOT / 'assets/brand/app_logo_mark.png'):
    export('mascot.webp', path)
export('mascot.webp', ROOT / 'branding/mooncat-logo.png', (512, 512))
for name, source in [('mark','mascot.webp'), ('banner','scene.webp'), ('splash','scene.webp'), ('profiles','scene.webp')]:
    export(source, RES / f'drawable-nodpi/mooncat_{name}_v102.webp', crop=name != 'mark')
export('poster.webp', RES / 'drawable-nodpi/mooncat_poster_v2.webp', (512, 768), crop=True)

# Render text separately so the app name is exact and legible at TV sizes.
word = Image.new('RGBA', (1200, 280))
draw = ImageDraw.Draw(word)
font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf', 154)
text = 'MoonCat'
b = draw.textbbox((0, 0), text, font=font, stroke_width=2)
draw.text(((1200-(b[2]-b[0]))/2, (280-(b[3]-b[1]))/2-b[1]), text, font=font,
          fill=(255, 244, 222, 255), stroke_width=2, stroke_fill=(108, 70, 137, 255))
word.save(ART / 'wordmark.png', optimize=True)
for path in RES.glob('drawable/app_logo_wordmark*.png'):
    export('wordmark.png', path)
export('wordmark.png', ROOT / 'assets/brand/app_logo_wordmark.png')
export('wordmark.png', RES / 'drawable-nodpi/mooncat_word_v102.webp')
print('Exported MoonCat v2 branding')
