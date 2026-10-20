# AJ Launcher for Fire TV

A small, ad-free launcher built for the user's Amazon Fire TV 55-inch 4-Series.

## Target device

- Retail model: `K24NE5`
- Hardware/model string: `AMZ_K24NE5_4K55N400A`
- Fire OS: `7.7.1.7 (PS7717/5741)`
- Fire TV Home: `7321873.1`
- User-visible storage: `11.34 GB`

## v0.1 features

- Clean **Favorites** and **Apps** rows
- Fire TV remote / D-pad navigation
- Fast focus animation with a clear focus border
- Hold **OK** to add or remove Favorites
- Clock and date
- Direct system Settings shortcut
- No ads
- No analytics or telemetry
- No network permission
- No background service
- Lightweight platform views instead of a heavy UI stack

## Cloud build

This repo includes a GitHub Actions workflow that builds the APK in the cloud.

Open:

**Actions → Build APK → latest run**

Then download the artifact named:

`AJ-Launcher-debug`

Inside it is:

`app-debug.apk`

So a local Linux build machine is not required for normal builds.

## Install over Wi-Fi with ADB

Enable **ADB Debugging** on the Fire TV and find its IP under:

**Settings → My Fire TV → About → Network**

Then from any ADB-capable device on the same network:

```bash
adb connect <TV-IP>:5555
adb install -r app-debug.apk
adb shell am start -n com.aj.firelauncher/.MainActivity
```

Approve the first debugging connection on the TV.

## Home button

Fire OS 7 does not normally let a sideloaded launcher permanently replace Amazon Home by itself.

The companion project **Home on Fire** can redirect the Home button to AJ Launcher:

https://github.com/toolicious/home-on-fire

Typical setup:

```bash
adb install home-on-fire.apk
adb shell pm grant io.github.toolicious.homeonfire android.permission.WRITE_SECURE_SETTINGS
```

Then open Home on Fire, enable its accessibility service, select **AJ Launcher**, and enable its Home-button / boot options.

## Install without Linux

See `INSTALL_FROM_PHONE.md`.

The short version: Linux is not inherently required. Once the APK is built, a phone/tablet can sideload it, and an ADB-capable device on the same LAN can handle the one-time network setup.
