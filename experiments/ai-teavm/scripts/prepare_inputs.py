"""Select original resources and hash unchanged Forge inputs; no game source editing."""
import hashlib
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
PROJECT = ROOT / 'experiments/ai-teavm'
BASELINE = 'db893dcdb14fdd85a05437ad458dc70ef704ca2e'
modules = ['forge-core', 'forge-game', 'forge-ai']
check = subprocess.run(['git', 'diff', '--exit-code', BASELINE, '--',
                        'pom.xml', *[m + '/src' for m in modules],
                        *[m + '/pom.xml' for m in modules]], cwd=ROOT)
if check.returncode:
    raise SystemExit('Original source/POM changed; refusing to prepare')
build = PROJECT / 'build'
build.mkdir(exist_ok=True)
rows = []
for module in modules:
    for folder in ['src/main/java', 'target/classes']:
        for file in sorted((ROOT / module / folder).rglob('*')):
            if file.is_file():
                raw = file.read_bytes()
                rows.append({'path': file.relative_to(ROOT).as_posix(),
                             'bytes': len(raw), 'sha256': hashlib.sha256(raw).hexdigest()})
for path in ['pom.xml', *[m + '/pom.xml' for m in modules]]:
    file = ROOT / path
    rows.append({'path': path, 'bytes': file.stat().st_size,
                 'sha256': hashlib.sha256(file.read_bytes()).hexdigest()})
manifest = {'baseline': BASELINE, 'inputs': rows}
(build / 'input-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
resources = {
    'bolt': 'forge-gui/res/cardsfolder/l/lightning_bolt.txt',
    'mountain': 'forge-gui/res/cardsfolder/m/mountain.txt',
    'language': 'forge-gui/res/languages/en-US.properties',
    'types': 'forge-gui/res/lists/TypeLists.txt',
}
generated = build / 'generated/probe/FixtureScripts.java'
generated.parent.mkdir(parents=True, exist_ok=True)
lines = ['package probe;', 'public final class FixtureScripts {']
selected = []
for name, path in resources.items():
    raw = (ROOT / path).read_bytes()
    selected.append({'path': path, 'bytes': len(raw), 'sha256': hashlib.sha256(raw).hexdigest(),
                     'lfSha256': hashlib.sha256(raw.replace(b'\r\n', b'\n')).hexdigest()})
    if name in ('language', 'types'):
        target = build / ('fixture/languages/en-US.properties' if name == 'language' else 'fixture/TypeLists.txt')
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(raw)
    else:
        strings = ',\n'.join(json.dumps(s) for s in raw.decode('utf-8').splitlines())
        lines.append('public static final String[] ' + name.upper() + ' = {\n' + strings + '\n};')
lines.append('}')
generated.write_text('\n'.join(lines) + '\n', encoding='utf-8')
(PROJECT / 'evidence/fixture-inputs.json').write_text(json.dumps(selected, indent=2)+'\n', encoding='utf-8')
print('Original inputs hashed:', len(rows), '; genuine scripts embedded; English resource copied unchanged.')
