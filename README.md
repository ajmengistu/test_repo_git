# AJ Launcher for Fire TV

Minimal navy/blue home screen for Fire OS 7 and Android TV (Android 6+).

## v0.4

- Prime Video, Peacock and YouTube lead Favorites. Missing apps have a clear install message.
- Streaming Sites and Live Sports open the exact FMHY section URLs inside AJ Browser.
- All Apps starts collapsed. Press OK to expand/collapse, or Back to collapse.
- Settings & Tools contains Fire TV Settings, AJ Browser, NordVPN and Home on Fire when installed.
- Explicit D-pad neighbors, clear focus borders and focus restoration on app return/recreation.
- Hold OK on other installed apps to add/remove favorites. The three primary favorites stay pinned.
- The one-time v0.4 migration replaces the old automatic eight-app favorites list.
- AJ Browser retains its v0.3 remote controls, Shields behavior, host blocklist, popup prevention and Brave Search home page. Brave Search is a website inside AJ Browser; no Brave browser app is launched.

## Install / update

In Downloader enter:

https://github.com/ajmengistu/test_repo_git/releases/download/v0.4/AJ-Launcher-v0.4.apk

Enable installation permission for Downloader in Fire TV's Developer Options if prompted, download and choose Install.

**v0.3 was signed with a disposable CI debug key. v0.4 uses a persistent release key. An installed official v0.3 cannot be updated in place with this APK.** First make sure you can reach Amazon Home, then uninstall only AJ Launcher through Settings → Applications → Manage Installed Applications, and install v0.4. Uninstalling removes AJ Launcher's preferences and its embedded browser data; it does not uninstall Prime Video, Peacock, YouTube or NordVPN. Re-select AJ Launcher in Home on Fire if needed.

For later versions signed with the v0.4 key, install over the existing app and retain data. Do not uninstall for routine updates. `INSTALL_FAILED_UPDATE_INCOMPATIBLE` indicates mismatched signing keys; other install errors can have other causes.

ADB alternative:

```sh
adb install -r AJ-Launcher-v0.4.apk
adb shell am start -n com.aj.firelauncher/.MainActivity
```

The ADB update command also requires matching signatures. Uninstall v0.3 only after accepting the data loss described above.

## Build and validate

Use JDK 17, Gradle 8.10.2, Android platform 35 and build-tools 35.0.0.

```sh
gradle :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

Without signing environment variables the release APK is unsigned and must not be distributed. Set `AJ_KEYSTORE_PATH` and `AJ_KEYSTORE_PASSWORD` privately; the key alias is `aj-launcher`. Never commit keys or passwords.

The **Signed release** GitHub Actions workflow restores the persistent key from repository secrets `AJ_KEYSTORE_BASE64` and `AJ_KEYSTORE_PASSWORD`, runs tests/lint, builds a signed release, verifies its signature, publishes the selected existing tag and verifies the public download matches. It fails if signing secrets are missing and never overwrites an existing release. Maintain a secure offline backup before rotating or deleting these secrets.

The **Build APK** workflow produces an isolated debug preview (`com.aj.firelauncher.preview`) so it cannot replace the installed launcher. Production releases use `com.aj.firelauncher`. Debug builds are not update packages.

Automated tests cover exact shortcut intents, browser popup settings, collapsed/expanded states, D-pad links and focus restoration. Physical Fire TV behavior, installed-app availability and live websites still depend on the device.

## Home button

Fire OS may require [Home on Fire](https://github.com/toolicious/home-on-fire) to redirect Home to AJ Launcher. Existing Home on Fire behavior is unchanged.

No telemetry or background service. Internet permission is used by the embedded browser.
