#!/usr/bin/env python3
"""Build pinned, source-only filter engine for Fire TV/Android ARM (NDK API 23)."""
import json, os, pathlib, shutil, subprocess
root = pathlib.Path(__file__).resolve().parents[1]
crate = root / 'native/blocker'
ndk_var = os.environ.get('ANDROID_NDK_HOME') or os.environ.get('ANDROID_NDK_ROOT')
if ndk_var:
    ndk = pathlib.Path(ndk_var)
else:
    sdk = pathlib.Path(os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT', '')))
    installed = sorted((sdk / 'ndk').glob('*'), key=lambda p: tuple(int(x) for x in p.name.split('.')))
    if not installed: raise SystemExit('Android NDK required. Set ANDROID_NDK_HOME.')
    ndk = installed[-1]
toolchain = next((ndk / 'toolchains/llvm/prebuilt').glob('*')) / 'bin'
for target, abi, clang in [('armv7-linux-androideabi','armeabi-v7a','armv7a-linux-androideabi23-clang'),
                           ('aarch64-linux-android','arm64-v8a','aarch64-linux-android23-clang')]:
    env = os.environ.copy()
    env['CARGO_TARGET_' + target.upper().replace('-', '_') + '_LINKER'] = str(toolchain / clang)
    subprocess.run(['cargo','build','--locked','--release','--target',target],cwd=crate,env=env,check=True)
    output = root / 'app/build/generated/blockerJni' / abi
    output.mkdir(parents=True,exist_ok=True)
    shutil.copy2(crate / 'target' / target / 'release/libajblock.so', output / 'libajblock.so')

# Carry the pinned Rust dependency attributions/license texts in the distributed APK.
metadata = json.loads(subprocess.check_output(['cargo','metadata','--locked','--format-version','1'],cwd=crate))
notices = []
for package in sorted(metadata['packages'],key=lambda p:p['name']):
    if package['name'] == 'ajblock': continue
    folder = pathlib.Path(package['manifest_path']).parent
    notices.append(package['name']+' '+package['version']+' — '+str(package.get('license'))+'\n'+str(package.get('repository'))+'\n')
    for license_file in sorted(set(folder.glob('LICENSE*')) | set(folder.glob('COPYING*'))):
        if license_file.is_file(): notices.append(license_file.read_text(errors='replace'))
output = root/'app/src/main/assets/licenses/native-dependencies.txt'
output.write_text('\n\n'.join(notices))
