# Install without opening Linux

Once the APK is built, Linux is not required.

## Easy route

1. Download the APK from the GitHub Actions artifact to a device you control.
2. Make it reachable by the Fire TV, or use Amazon Downloader with a direct HTTPS URL.
3. Allow Downloader to install unknown apps when Fire OS asks.
4. Install AJ Launcher.

## Network ADB route

Any ADB-capable device on the same LAN can connect to:

```text
<TV-IP>:5555
```

Approve the debugging prompt on the Fire TV, then install the APK.

For the one-time Home-button redirection setup, Home on Fire may need:

```bash
adb shell pm grant io.github.toolicious.homeonfire android.permission.WRITE_SECURE_SETTINGS
```

So Linux is not inherently required. Something on the same network simply needs to act as the ADB client.
