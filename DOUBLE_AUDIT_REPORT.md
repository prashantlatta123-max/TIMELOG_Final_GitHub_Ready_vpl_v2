# TIMELOG Double Audit Report

## Pass 1 — structure and feature wiring
- Android manifest XML parsed successfully.
- Notification status-icon vector XML parsed successfully.
- GitHub Actions workflow contains JDK 17, Android SDK 35, Gradle 8.9, debug APK build, and artifact upload.
- Navigation destinations were cross-checked against Compose screen functions.
- Old placeholder text for timeline, usage history, and backup screens was removed.
- Room database, local JSON backup/restore, per-app notification rules, event-derived hourly tracking, sleep event logic, privacy, and search modules were found in source.

## Pass 2 — platform and source consistency
- minSdk 29 (Android 10), compileSdk 35, targetSdk 35.
- Usage Access and optional notification permissions are declared.
- No INTERNET, SYSTEM_ALERT_WINDOW, Accessibility Service, device-admin, or QUERY_ALL_PACKAGES permission is declared.
- Launcher icon density assets and master TIMELOG logo are present.
- Kotlin source delimiter-balance scan passed.
- Backup format includes daily app usage, per-app hourly records, sleep records, theme preferences, alert rules, and local sleep settings.
- Smart alerts use Android notifications only; no overlay UI is implemented.
- Sleep estimation uses screen non-interactive and keyguard-unlock UsageEvents, a 65-minute inactivity threshold, and at least five minutes of foreground use after unlock.

## Verification boundary
This environment does not contain Gradle or an Android SDK, so a real Android Gradle compilation, emulator run, and device/OEM test could not be executed here. The included GitHub Actions workflow is the first actual compilation gate. A successful CI run is required before calling the APK build verified.

## Known Android platform limitations
UsageStats / UsageEvents and WorkManager are subject to OEM behavior, data retention, Doze, battery restrictions, and scheduler delays. App foreground transitions are an approximation of app openings; they are not literal launcher-tap counts. Sleep is a phone-inactivity estimate, not medical sleep tracking. Lifetime history starts when Usage Access and the periodic collector are enabled; it cannot reconstruct older Android records that are no longer available.
