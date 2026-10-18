"""Rebuild BLOODHELPER artwork from the pinned Bloodstone resource-pack artwork.

Requires Python 3 and Pillow. No network requests are made by this script.
The original BLOOD lettering and pack emblem are retained. The emblem belongs
between BLOOD and HELPER in the image itself. HELPER is rebuilt from the real
Bloodstone B/L/O/E surfaces and proportions, with matching block extrusion.
"""
from __future__ import annotations

from pathlib import Path
import hashlib
import json
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent
OUT = ROOT.parent / "src/main/resources/assets/bloodhelper"
TEX = OUT / "textures"
S = 4
W, H = 1253, 158
FACE_HEIGHT = 106
HELPER_X = 651
CANVAS = (W * S, H * S)

# Coordinates are measured from the real Bloodstone B, L, O and E fronts.
# The 31-35px stems and 16-20px counters are deliberately retained: the first
# version's wider holes and thin outlines did not have the source's weight.
SHAPES = {
    "H": [(0,0),(32,0),(32,43),(52,43),(52,0),(83,0),
          (83,106),(52,106),(52,65),(32,65),(32,106),(0,106)],
    "E": [(0,0),(86,0),(86,21),(32,21),(32,42),(77,42),
          (77,63),(32,63),(32,83),(86,83),(86,106),(0,106)],
    "L": [(0,0),(35,0),(35,83),(77,83),(77,106),(0,106)],
    "P": [(0,0),(84,0),(84,44),(73,44),(73,64),(31,64),(31,106),(0,106)],
    "R": [(0,0),(84,0),(84,44),(73,44),(73,62),(55,62),
          (84,106),(51,106),(31,75),(31,106),(0,106)],
}
COUNTERS = {"P": [(31,20),(47,20),(47,43),(31,43)],
            "R": [(31,20),(47,20),(47,43),(31,43)]}
WIDTHS = {"H":83,"E":86,"L":77,"P":84,"R":84}
# Native x at y=8, native front-face shear over 106px, canonical width.
SOURCE_FACES = {"B": (35,-24,84), "L": (130,-18,77),
                "O": (214,-13,83), "E": (900,28,86)}


def native_faces(original):
    faces = {}
    for name, (start, shear, width) in SOURCE_FACES.items():
        faces[name] = original.transform((width, FACE_HEIGHT),Image.Transform.AFFINE,
            (1,shear/105,start,0,1,8),Image.Resampling.BICUBIC)
    # Reuse the actual stone surface from E for all red-derived letter shapes.
    # Native cracks and hairline bevels are retained as dark/light detail.
    stone = faces["E"]
    red_baseline=[max(1,faces["L"].getpixel((15,y))[0]) for y in range(FACE_HEIGHT)]
    for name in ["B", "L", "O"]:
        face = faces[name]
        for y in range(FACE_HEIGHT):
            material = stone.getpixel((17,y))[:3]
            red_level = red_baseline[y]
            for x in range(face.width):
                red, green, blue, alpha = face.getpixel((x,y))
                if red > 30 and red > green * 1.4:
                    factor = min(1.08,red / red_level)
                    face.putpixel((x,y),tuple(min(255,round(v*factor)) for v in material)+(alpha,))
    return faces


def front_texture(char, faces):
    src = faces[{"H":"O","E":"E","L":"L","P":"B","R":"B"}[char]]
    tex = Image.new("RGBA",(WIDTHS[char],FACE_HEIGHT))
    tex.alpha_composite(src)
    draw = ImageDraw.Draw(tex)
    # Reconstruct only the new joins; all other letter surfaces are native art.
    if char == "H":
        for y in range(43,65):
            draw.line((30,y,54,y),fill=faces["E"].getpixel((17,y)),width=1)
        # The source O has a long rectangular slot; the H opens at both ends.
    elif char in ("P","R"):
        for y in range(63,106):
            for x in range(tex.width):
                # P stem / R leg use the same unbroken stone material. The
                # source B's second counter must not survive in the new glyph.
                if x >= 28:
                    tex.putpixel((x,y),faces["E"].getpixel((17,y)))
    return tex


def point(x, y, depth=0):
    # Native STONE fans gently outward on the front and recedes toward the
    # word's left edge on its 32px-deep bottom plane.
    front_x = HELPER_X + x * (1 + .00048*y)
    back_x = front_x - x * .00165 * depth
    return round(back_x*S), round((8+y+depth)*S)


def contour(points, start, depth=0):
    return [point(start+x,y,depth) for x,y in points]


def render_logo():
    original = Image.open(ROOT/"source/bloodstone-hd.png").convert("RGBA")
    faces = native_faces(original)
    canvas = Image.new("RGBA",CANVAS)
    solids = Image.new("RGBA",CANVAS)
    draw = ImageDraw.Draw(solids)
    placements = []
    x = 0
    for char in "HELPER":
        placements.append((char,x))
        # A solid 32px extrusion, with the source's neutral charcoal material.
        # Rear silhouettes are laid down first, including the letter counters.
        for depth in range(32,-1,-1):
            draw.polygon(contour(SHAPES[char],x,depth),fill=(67,67,67,255))
            if char in COUNTERS:
                draw.polygon(contour(COUNTERS[char],x,depth),fill=(8,8,8,255))
        x += WIDTHS[char]+11
    # The source has a real 7-8px black silhouette, not a 1px UI border.
    outline=solids.getchannel("A").filter(ImageFilter.MaxFilter(57))
    canvas.paste((0,0,0,255),(0,0,W*S,H*S),outline)
    canvas.alpha_composite(solids)

    # Warp each native front independently into the same perspective plane.
    # Supersampling preserves source cracks without inventing font contours.
    for char, start in placements:
        front=front_texture(char,faces).resize((WIDTHS[char]*S,FACE_HEIGHT*S),Image.Resampling.BICUBIC)
        local_mask=Image.new("L",front.size)
        ld=ImageDraw.Draw(local_mask)
        ld.polygon([(a*S,b*S) for a,b in SHAPES[char]],fill=255)
        if char in COUNTERS:
            ld.polygon([(a*S,b*S) for a,b in COUNTERS[char]],fill=0)
        front.putalpha(local_mask)
        # Per-row affine stretch reproduces the front face's native perspective.
        for y in range(FACE_HEIGHT*S):
            fy=y/S
            factor=1+.00048*fy
            row=front.crop((0,y,front.width,y+1))
            row=row.resize((max(1,round(front.width*factor)),1),Image.Resampling.BICUBIC)
            canvas.alpha_composite(row,(round((HELPER_X+start*factor)*S),8*S+y))

    # Carry a few real face fractures onto the lower extrusion, as in STONE.
    detail=ImageDraw.Draw(canvas)
    for char,start in placements:
        if char == "L":
            detail.line(contour([(18,103),(18,111),(23,116)],start),fill=(8,8,8,255),width=2*S)
        elif char == "E":
            detail.line(contour([(69,106),(67,113),(72,113)],start),fill=(8,8,8,255),width=2*S)
    canvas=canvas.resize((W,H),Image.Resampling.LANCZOS)
    # These two original elements are preserved exactly at native/integer scale.
    canvas.alpha_composite(original.crop((0,0,496,153)),(0,0))
    heart=Image.open(ROOT/"source/bloodstone-pack.png").convert("RGBA")
    canvas.alpha_composite(heart.resize((128,128),Image.Resampling.NEAREST),(503,10))
    canvas.save(TEX/"logo.png")
    canvas.save(ROOT/"bloodhelper-logo.png")
    return canvas


def main():
    TEX.mkdir(parents=True, exist_ok=True)
    logo=render_logo()
    source_icon=Image.open(ROOT/"source/bloodstone-pack.png").convert("RGBA")
    source_icon.resize((256,256),Image.Resampling.NEAREST).save(OUT/"icon.png")
    source_icon.resize((256,256),Image.Resampling.NEAREST).save(TEX/"icon.png")
    source_icon.save(ROOT/"bloodhelper-icon-64.png")
    source_icon.resize((256,256),Image.Resampling.NEAREST).save(ROOT/"bloodhelper-icon-256.png")
    # A plain inspection background makes the transparent art easy to review.
    # Do not introduce a second emblem or frame that is absent from the logo.
    preview=Image.new("RGBA",(logo.width+48,logo.height+48),(27,29,35,255))
    preview.alpha_composite(logo,(24,24))
    preview.convert("RGB").save(ROOT/"bloodhelper-preview.png")
    small=logo.resize((375,47),Image.Resampling.LANCZOS)
    small_preview=Image.new("RGBA",(407,79),(27,29,35,255))
    small_preview.alpha_composite(small,(16,16))
    small_preview.convert("RGB").save(ROOT/"bloodhelper-preview-375.png")
    manifest={"logo":{"width":logo.width,"height":logo.height,"texture":"bloodhelper:textures/logo.png",
                       "composition":["BLOOD","bloodstone-pack heart","HELPER"],
                       "heartBounds":{"x":503,"y":10,"width":128,"height":128},
                       "helper":{"method":"reconstructed from native Bloodstone B/L/O/E fronts",
                                 "sourceFaces":list(SOURCE_FACES),"frontHeight":106,
                                 "outlineRadius":7,"extrusionDepth":32}},
              "icon":{"width":256,"height":256,"texture":"bloodhelper:textures/icon.png"},
              "sourceCommit":"782ea4fc458a7f3d4c9738629d2d2ad3ff59676f",
              "sourceRepository":"https://github.com/Trard/bloodstone3",
              "files":{}}
    for path in sorted((ROOT/"source").glob("*.png")):
        manifest["files"][path.name]={"sha256":hashlib.sha256(path.read_bytes()).hexdigest(),"bytes":path.stat().st_size}
    manifest["outputs"]={}
    for name in ["bloodhelper-logo.png","bloodhelper-preview.png","bloodhelper-preview-375.png"]:
        path=ROOT/name
        manifest["outputs"][name]={"sha256":hashlib.sha256(path.read_bytes()).hexdigest(),
                                    "bytes":path.stat().st_size}
    (ROOT/"manifest.json").write_text(json.dumps(manifest,indent=2)+"\n",encoding="utf-8")
    print(f'Logo: {logo.width}x{logo.height}; icon: 256x256; preview: {preview.width}x{preview.height}')


if __name__ == "__main__":
    main()
