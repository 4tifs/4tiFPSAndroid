# 4tif Android source

This Android app has two tabs:

- **Patcher**: patches and restores MP4/MOV timing metadata entirely on the device.
- **TikTok Web**: loads TikTok's upload site inside an Android WebView.

Open this folder as an Android project, then build an APK. The app requires Android 10 or newer.

## Build without Android Studio

Upload this whole folder to a private GitHub repository. The included GitHub Actions workflow builds an APK automatically whenever you push to the `main` branch. Open the repository's **Actions** tab, select **Build Android APK**, wait for it to finish, and download `4tif-debug-apk` from the run's **Artifacts** section. Transfer that APK to an Android phone and open it to install.

The output file is written to the device's Downloads folder. Test with copies of videos first.
