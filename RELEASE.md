# Release & Direct APK Distribution Guide

This document lists the steps to build signed release APKs (per-ABI and universal), verify 16 KB native page-size alignment, and publish the `update-manifest.json` file for the in-app update checker.

---

## 1. Bump `versionCode` and `versionName` (Single Source of Truth)

Both `versionCode` and `versionName` are defined in one place in [`gradle/libs.versions.toml`](gradle/libs.versions.toml) and referenced automatically by `app/build.gradle.kts`:

```toml
[versions]
appVersionCode = "2"
appVersionName = "1.1.0"
```

---

## 2. Configure Release Signing Credentials

Provide the upload keystore credentials via environment variables (used by `signingConfigs.release` in `app/build.gradle.kts`):

```bash
export KEYSTORE_PATH="/secure/path/to/my-upload-key.jks"
export STORE_PASSWORD="your-keystore-password"
export KEY_PASSWORD="your-key-password"
```

*(Note: The key alias is `upload`.)*

---

## 3. Build the Signed Release APKs

### Option A: Direct Website APK Build (In-App Update Checker Enabled)
By default, `BuildConfig.ENABLE_DIRECT_UPDATE_CHECKER` is `true`:

```bash
gradle :app:assembleRelease
```

To override the default HTTPS update manifest URL at build time:

```bash
gradle :app:assembleRelease \
  -PenableDirectUpdateChecker=true \
  -PupdateManifestUrl="https://releases.pdfallinone.app/android/update-manifest.json"
```

### Option B: Google Play Build (In-App Update Checker Disabled)
Pass `-PenableDirectUpdateChecker=false` so `BuildConfig.ENABLE_DIRECT_UPDATE_CHECKER` is compiled as `false` and R8 strips the self-update flow:

```bash
gradle :app:assembleRelease -PenableDirectUpdateChecker=false
```

### Output APKs (ABI Splits + Universal APK)
R8 code shrinking (`isMinifyEnabled = true`) and resource shrinking (`isShrinkResources = true`) run automatically on `release`. The signed APKs are generated in `app/build/outputs/apk/release/`:

- `app-arm64-v8a-release.apk` (64-bit ARM devices)
- `app-armeabi-v7a-release.apk` (32-bit ARM devices)
- `app-x86_64-release.apk` (64-bit x86_64 tablets/ChromeOS)
- `app-universal-release.apk` (Universal APK containing all supported ABIs — recommended for the website `apkUrl`)

---

## 4. Verify 16 KB Native Page Alignment & APK Signature

All native `.so` libraries are packaged uncompressed and aligned on 16 KB page boundaries (`packaging.jniLibs.useLegacyPackaging = false`). Verify before publishing:

```bash
# 1. Verify 16 KB (16384-byte) page alignment for uncompressed shared libraries
zipalign -c -P 16 -v 4 app/build/outputs/apk/release/app-universal-release.apk

# 2. Verify v2/v3 APK signature
apksigner verify --verbose app/build/outputs/apk/release/app-universal-release.apk
```

---

## 5. Generate SHA-256 & Publish the Update Manifest JSON

1. Compute the lowercase hexadecimal SHA-256 digest of the signed release APK:

```bash
sha256sum app/build/outputs/apk/release/app-universal-release.apk
```

2. Upload the signed APK to your HTTPS release server (e.g., `https://releases.pdfallinone.app/android/pdf-all-in-one-v1.1.0.apk`).

3. Publish the JSON manifest at `https://releases.pdfallinone.app/android/update-manifest.json` with the required fields (`versionCode`, `versionName`, `apkUrl`, `sha256`, `minSdk`, `changelog`):

```json
{
  "versionCode": 2,
  "versionName": "1.1.0",
  "apkUrl": "https://releases.pdfallinone.app/android/pdf-all-in-one-v1.1.0.apk",
  "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
  "minSdk": 26,
  "changelog": "- Faster PDF rendering and lower memory footprint\n- Improved Bengali conjunct font subsetting\n- Security and stability enhancements"
}
```

### How the In-App Update Checker Works
- **Startup Check (At Most Once a Day)**: On app launch, if `BuildConfig.ENABLE_DIRECT_UPDATE_CHECKER` is `true` and at least 24 hours have passed since the last automatic check, the app fetches the JSON manifest over HTTPS.
- **Manual Check**: Users can tap **"Check for updates"** in **Settings** or **About** at any time, and can configure a custom HTTPS manifest URL in Settings.
- **Explicit User Consent & SHA-256 Verification**: When `manifest.versionCode > BuildConfig.VERSION_CODE` and `Build.VERSION.SDK_INT >= manifest.minSdk`, the app displays a dialog with the `changelog`. Only after the user confirms does the app download the APK with progress, verify its SHA-256 hash against `manifest.sha256`, guide the user to allow installs from this app if required (`REQUEST_INSTALL_PACKAGES`), and commit an Android `PackageInstaller` session.
