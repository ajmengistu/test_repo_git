# Browser components and filter sources

AJ Browser remains an Android System WebView browser. It does not implement Chromium or Brave's native Shields. No analytics, Acceptable Ads subscription, runtime code downloads, filter auto-update service, or extra Android permissions are added.

| Component | Source and pinned version | License / integration decision |
|---|---|---|
| Brave adblock-rust | [source](https://github.com/brave/adblock-rust/tree/6028a6d5735f1e81346eeef73c194c7240035248), 0.13.3 at the pinned revision | MPL-2.0. Actively maintained rule engine, built from source for ARMv7/ARM64, API 23+. Thin JNI wrapper; upstream engine files are unmodified. Network matching and CSS selectors run locally. |
| jni-rs | [source](https://github.com/jni-rs/jni-rs), 0.21.1 | MIT / Apache-2.0. Small pinned JNI adapter dependency. `Cargo.lock` pins transitive versions and checksums. Native dependency notices are collected from their source packages into APK assets during builds. |
| mrowser | [source](https://github.com/m-salehi-v/mrowser/tree/dcfdcb666cc4a743437093544f63c34e72f98c1a), reviewed October 21, 2026 | MIT, copyright 2026 Mohammad Salehivaziri. Cursor design informed this Java implementation: frame-based acceleration, native mouse/wheel events and hit testing of nested scrollers. Its player, update checker, dependencies and host lists are not included. Full MIT notice is bundled. |
| EasyList / EasyPrivacy | [EasyList](https://easylist.to/easylist/easylist.txt), [EasyPrivacy](https://easylist.to/easylist/easyprivacy.txt), snapshots October 21, 2026 | Distributed under CC BY-SA 3.0. Attribution: The EasyList authors, https://easylist.to/. Bundled snapshots are unmodified; retain upstream headers. See `app/src/main/assets/licenses/easylist-CC-BY-SA-3.0.txt`. |
| AdGuard Base + EasyList | [official uBlock-compatible filter](https://filters.adtidy.org/extension/ublock/filters/2.txt), snapshot October 21, 2026 | GPL-3.0; upstream portions retain their licenses. The standalone list is bundled unmodified, alongside its full GPL license. User-facing blocking does not load AdGuard code or extensions. |

Engine and filter license texts are shipped in `app/src/main/assets/licenses/`. Asset hashes and source links are recorded in `app/src/main/assets/filters/sources.json`. Source for the adapter is in `native/blocker/`; the exact engine revision is in its Cargo manifest/lockfile. Rebuilds do not silently fetch newer filters. Refresh bundled lists deliberately when releasing an update.

## Alternatives reviewed

- [libadblockplus-android](https://github.com/adblockplus/libadblockplus-android) documents JCenter, NDK r16b and its own V8 builds, plus additional network/Wi-Fi permissions. That legacy integration is a poor fit for this small existing WebView app.
- [AdGuard TSUrlFilter](https://github.com/AdguardTeam/tsurlfilter) is maintained and provides extensive rules, but its browser-extension integration and TypeScript runtime are not a direct Android WebView request interceptor.
- GeckoView/Chromium replacements would substantially expand the app and replace its working browser architecture. They are not added.

## Scope and limitations

- Network rules use `WebViewClient.shouldInterceptRequest` and API 24+ service-worker interception. WebView does not expose every initiating frame/type, WebSocket traffic, response body, cache/service-worker path or every redirect hop. Request type is taken from fetch headers, then conservative MIME/extension hints.
- Top-level ad destinations and gesture-initiated popup destinations are filtered. Non-gesture popups do not navigate the main page. Genuine app links require a user gesture/recent Select, use browsable VIEW intents, and cannot carry arbitrary parsed-intent components/extras.
- Cosmetic hiding supports ordinary CSS selectors, including domain rules and dynamic class/id selectors. Procedural actions, resource-replacement/scriptlet libraries and request parameter rewriting are not integrated. Unsupported rules are not approximated by broad overlay deletion. All ads/sponsored material cannot be guaranteed to disappear.
- Blocking can be paused for the current site for the browser session. Normal filter exception rules remain enabled to avoid breaking legitimate sites; no Acceptable Ads subscription is loaded.
- The prior player compatibility workaround that relaxes iframe sandbox attributes is retained behind a per-site setting (default on, preserving existing behavior). It can be switched off on untrusted sites. Cookies and third-party-cookie behavior are preserved.
- Desktop mode advertises the installed WebView's actual Chromium version. Wide viewport/overview and zoom use WebView APIs; no CSS page transforms or viewport-meta rewriting. Zoom percentages are relative to fitted overview and bounded by the page/WebView's supported scale. Text size is independent. Sites and old Fire OS WebView versions can limit desktop rendering, codecs and DRM.
