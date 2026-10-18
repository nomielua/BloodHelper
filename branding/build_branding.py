"""Prepare the user's BLOODHELPER image without redrawing its lettering.

Python 3 + Pillow. The exact supplied image is only cropped and its connected
exterior backdrop made transparent. All RGB values are preserved.
"""
from __future__ import annotations
from collections import deque
from pathlib import Path
import hashlib
import json
from PIL import Image

ROOT=Path(__file__).resolve().parent
SOURCE=ROOT/'source/user-logo-2026-10-12.png'
TEXTURE=ROOT.parent/'src/main/resources/assets/bloodhelper/textures/logo.png'
PANEL_CROP=(0,160,2172,535)
PADDING=4


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def prepare_logo():
    original=Image.open(SOURCE).convert('RGBA')
    if original.size!=(2172,724):
        raise ValueError('Source must remain the unmodified 2172x724 user image')
    panel=original.crop(PANEL_CROP)
    width,height=panel.size
    pixels=panel.load()
    visited=bytearray(width*height)
    queue=deque()
    for x in range(width):
        queue.extend(((x,0),(x,height-1)))
    for y in range(height):
        queue.extend(((0,y),(width-1,y)))
    # Flood ONLY from the perimeter: the solid black outer lettering border
    # protects all enclosed material, counters and cracks. No erosion/repaint.
    while queue:
        x,y=queue.popleft()
        index=y*width+x
        if visited[index]:
            continue
        visited[index]=1
        red,green,blue,alpha=pixels[x,y]
        channels=(red,green,blue)
        if min(channels)<3 or max(channels)>70 or max(channels)-min(channels)>15:
            continue
        pixels[x,y]=(red,green,blue,0)
        if x:
            queue.append((x-1,y))
        if y:
            queue.append((x,y-1))
        if x+1<width:
            queue.append((x+1,y))
        if y+1<height:
            queue.append((x,y+1))
    box=panel.getchannel('A').getbbox()
    if box is None:
        raise ValueError('No foreground in the selected image')
    padded=(box[0]-PADDING,box[1]-PADDING,box[2]+PADDING,box[3]+PADDING)
    logo=panel.crop(padded)
    bounds=(padded[0]+PANEL_CROP[0],padded[1]+PANEL_CROP[1],
            padded[2]+PANEL_CROP[0],padded[3]+PANEL_CROP[1])
    if logo.convert('RGB').tobytes()!=original.crop(bounds).convert('RGB').tobytes():
        raise AssertionError('Lettering RGB must not change')
    return logo,bounds


def make_preview(logo,path,width=None):
    if width is not None:
        artwork=logo.resize((width,round(logo.height*width/logo.width)),Image.Resampling.LANCZOS)
    else:
        artwork=logo
    preview=Image.new('RGBA',(artwork.width+32,artwork.height+32),(27,29,35,255))
    preview.alpha_composite(artwork,(16,16))
    preview.convert('RGB').save(path)
    return preview.size


def main():
    logo,bounds=prepare_logo()
    TEXTURE.parent.mkdir(parents=True,exist_ok=True)
    logo.save(ROOT/'bloodhelper-logo.png')
    logo.save(TEXTURE)
    preview_size=make_preview(logo,ROOT/'bloodhelper-preview.png')
    small_size=make_preview(logo,ROOT/'bloodhelper-preview-375.png',375)
    manifest={
        'logo':{'width':logo.width,'height':logo.height,
            'texture':'bloodhelper:textures/logo.png','composition':['BLOOD','HELPER'],
            'source':'source/user-logo-2026-10-12.png','sourceType':'exact user-supplied image',
            'sourceDimensions':[2172,724],'sourceBoundsIncludingPadding':list(bounds),
            'processing':'crop and connected exterior backdrop alpha only',
            'foregroundRgbChanged':False},
        'icon':{'width':256,'height':256,'texture':'bloodhelper:textures/icon.png',
            'source':'source/bloodstone-pack.png','unchanged':True,
            'sourceCommit':'782ea4fc458a7f3d4c9738629d2d2ad3ff59676f',
            'sourceRepository':'https://github.com/Trard/bloodstone3'},
        'previewDimensions':list(preview_size),'preview375Dimensions':list(small_size),
        'files':{},'outputs':{}}
    for path in sorted((ROOT/'source').glob('*.png')):
        manifest['files'][path.name]={'sha256':sha256(path),'bytes':path.stat().st_size}
    for name in ('bloodhelper-logo.png','bloodhelper-preview.png','bloodhelper-preview-375.png'):
        path=ROOT/name
        manifest['outputs'][name]={'sha256':sha256(path),'bytes':path.stat().st_size}
    (ROOT/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    print(f'User image: {logo.width}x{logo.height}; source bounds: {bounds}; RGB unchanged')


if __name__=='__main__':
    main()
