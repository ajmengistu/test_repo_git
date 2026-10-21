#!/usr/bin/env python3
"""Build pinned, source-only filter engine for Fire TV/Android ARM (NDK API 23)."""
import os, pathlib, shutil, subprocess
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
