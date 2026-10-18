"""Build the two local UI faces; requires fonttools (only for regenerating assets)."""
from pathlib import Path
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
import hashlib
import json

here = Path(__file__).resolve().parent
root = here.parents[1]
source = here / 'Inter-variable.ttf'
manifest = json.loads((here / 'manifest.json').read_text())
if hashlib.sha256(source.read_bytes()).hexdigest() != manifest['download_sha256']:
    raise SystemExit('Unexpected source font hash')
output = root / 'src/main/resources/assets/bloodhelper/font'
output.mkdir(parents=True, exist_ok=True)
for stem, axes in manifest['instances'].items():
    font = TTFont(source)
    instantiateVariableFont(font, axes, inplace=True)
    weight = axes['wght']
    style = 'Regular' if weight <= 400 else 'Medium' if weight <= 550 else 'SemiBold'
    names = {1: 'BloodHelper UI', 2: style, 3: f'BloodHelperUI-{weight}',
             4: f'BloodHelper UI {weight}', 6: f'BloodHelperUI-{weight}',
             16: 'BloodHelper UI', 17: style}
    for entry in font['name'].names:
        if entry.nameID in names:
            entry.string = names[entry.nameID].encode(entry.getEncoding())
    font.save(output / f'{stem}.ttf')
print('Built static UI fonts with Cyrillic. Java build uses the bundled TTFs directly.')
