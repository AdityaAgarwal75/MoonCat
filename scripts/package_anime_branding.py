"""Size the generated brand artwork for existing Android resource contracts.

Creative artwork is generated with the built-in image generation tool. This script
only exports dimensions/formats; it never changes app layouts or theme colors.
"""
from pathlib import Path
from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'branding/anime'
RES = ROOT / 'app/src/main/res'


def export(source, target, size=None):
    path = ROOT / target if isinstance(target, str) else target
    image = Image.open(ART / source).convert('RGBA')
    size = size or Image.open(path).size
    image = ImageOps.pad(image, size, method=Image.Resampling.LANCZOS, color=(0, 0, 0, 0))
    if path.suffix == '.webp':
        image.save(path, quality=90, method=6)
    else:
        image.save(path, optimize=True)


for path in RES.glob('mipmap-*/ic_launcher*.png'):
    export('mascot.png', path)
for path in RES.glob('mipmap-*/banner*.png'):
    export('banner.png', path)
for path in RES.glob('drawable/app_logo_wordmark*.png'):
    export('wordmark.png', path)
for folder in ['drawable', 'drawable-nodpi']:
    path = RES / folder / 'tv_banner.png'
    if path.exists():
        export('banner.png', path)
export('mascot.png', RES / 'drawable/app_logo_mark.png')
export('mascot.png', RES / 'drawable/ic_launcher.png')
export('mascot.png', 'assets/brand/app_logo_mark.png')
export('wordmark.png', 'assets/brand/app_logo_wordmark.png')
export('mascot.png', 'branding/mooncat-logo.png', (512, 512))
for name, source in [('mark', 'mascot.png'), ('word', 'wordmark.png'),
                     ('banner', 'banner.png'), ('splash', 'banner.png'), ('profiles', 'banner.png')]:
    export(source, RES / f'drawable-nodpi/mooncat_{name}_v102.webp')
print('Exported anime branding at existing Android resource sizes')
