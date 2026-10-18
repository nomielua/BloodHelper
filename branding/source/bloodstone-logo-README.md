# Logo textures

Logo textures are kept within the resource-pack font-atlas limit. A split logo is resized proportionally to a total width of
`255 * fragment count`, then divided into equal 255-pixel cells. Each cell is registered as a bitmap font glyph; aliases join
adjacent glyphs with a negative-advance spacer so the cells render without seams. `U+E45C` is the normal one-pixel spacer;
Bloodstone's small alias uses `U+E45D` (`-2`) to compensate for its fractional 18px/77px bitmap scale.

## Bloodstone

The Invasion HUD joins the four HD `bloodstone_big` cells and downsamples with Lanczos
into `textures/invasion_logo.png` (255×39, transparent). `scripts/build_invasion_branding.py`
keeps the original proportions with a transparent bottom margin and no address. The 1.21.11 shader applies
bilinear filtering only to this image; font glyphs and faction flags retain nearest sampling.
`minecraft:invasion_branding` maps U+E700 (``) to an 85×13 GUI-pixel image;
its reserved ascent anchors it to the horizontal center at y=4, above the timer at y=22.
The glyph advance remains 86 pixels, compatible with BloodInvasion 1.9.39 and newer.
It is not in the default font.

| Variant | Source | Output cells | Cell size | Font height | Translation key |
| --- | --- | --- | --- | --- | --- |
| Large | `new/mofdde2l.png` | `textures/bloodstone_big/logo_1.png`–`logo_4.png` | 255×153 | 24 | `bloodstone.logo.bloodstone.large` |
| Small | `new/mofdde2l.png` | `textures/bloodstone_small/logo_1.png`–`logo_2.png` | 255×77 | 18 | `bloodstone.logo.bloodstone.small` |

The source is 1919×288. The large alias uses a proportional 1020×153 resize and four cells; the small alias uses a
proportional 510×77 resize and two cells. Every generated cell is exactly 255 pixels wide and no dimension exceeds 255 pixels.
The six new glyphs use `U+F827`–`U+F82C`; the English and Russian aliases are kept identical because they contain only bitmap glyphs.
