"""Package the built mod and reviewable sources without Gradle caches or game profiles."""
from pathlib import Path
import hashlib
import shutil
import zipfile

root = Path(__file__).resolve().parents[1]
dist = root.parent / 'dist'
dist.mkdir(exist_ok=True)
version = '1.1.0+1.21.11'
jar = root / 'build/libs' / f'BloodHelper-{version}.jar'
if not jar.is_file():
    raise SystemExit('Run gradlew build first')
shutil.copy2(jar, dist / jar.name)
for source, name in [
    ('branding/bloodhelper-logo.png', 'BLOODHELPER-logo.png'),
    ('branding/bloodhelper-icon-256.png', 'BLOODHELPER-icon.png'),
    ('branding/bloodhelper-preview.png', 'BLOODHELPER-preview.png'),
    ('README_RU.md', 'README_RU.md'),
    ('THIRD_PARTY_NOTICES.md', 'THIRD_PARTY_NOTICES.md'),
]:
    shutil.copy2(root / source, dist / name)
shutil.copytree(root / 'docs', dist / 'docs', dirs_exist_ok=True)
shutil.copytree(root / 'branding', dist / 'branding', dirs_exist_ok=True)
shots = dist / 'screenshots'
shots.mkdir(exist_ok=True)
for source in sorted((root / 'run-smoke/screenshots').glob('bloodhelper-*.png')):
    # Screenshot.grab replaces the explicitly supplied filename on the final run.
    shutil.copy2(source, shots / source.name)
allowed_dirs = {'src', 'branding', 'docs', 'gradle', 'tools'}
allowed_files = {'build.gradle', 'settings.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat',
                 '.gitignore', 'README_RU.md', 'THIRD_PARTY_NOTICES.md'}
archive = dist / f'BloodHelper-{version}-sources.zip'
with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
    for source in sorted(root.rglob('*')):
        if not source.is_file():
            continue
        rel = source.relative_to(root)
        if rel.parts[0] not in allowed_dirs and str(rel) not in allowed_files:
            continue
        if '__pycache__' in rel.parts or source.suffix in {'.pyc', '.log'}:
            continue
        z.write(source, Path('BloodHelper') / rel)
with (dist / 'SHA256SUMS.txt').open('w', encoding='utf-8', newline='\n') as out:
    for file in sorted(dist.rglob('*')):
        if file.is_file() and file.name != 'SHA256SUMS.txt':
            out.write(f'{hashlib.sha256(file.read_bytes()).hexdigest()}  {file.relative_to(dist).as_posix()}\n')
print(f'Packaged {jar.name} and {archive.name} in {dist}')
