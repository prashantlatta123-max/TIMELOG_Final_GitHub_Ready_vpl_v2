# TIMELOG — GitHub-ready Android project

TIMELOG is an offline-first digital wellbeing and phone-inactivity estimator for Android 10+.

## Build requirements
- Android Studio with JDK 17
- Android SDK Platform 35
- Gradle 8.9 (GitHub Actions installs this version)
- Minimum Android version: Android 10 / API 29

## Build on GitHub
1. Upload the contents of this folder to the root of your GitHub repository.
2. Keep `.github/workflows/android-build.yml` at that exact path.
3. Open the repository's Actions tab.
4. Run `Android CI` or push to `main` / `master`.
5. Download `TIMELOG-debug-apk` from the successful workflow run's Artifacts.

The workflow runs `:app:assembleDebug` with JDK 17 and Gradle 8.9.

## Implemented modules
- Sketch-based minimal dashboard: centered circular active-app-time indicator and four quick tiles.
- Minimal hamburger navigation drawer.
- Frost White and Pure Black themes with saved theme preference.
- Android Usage Access onboarding and privacy status.
- App foreground duration and package foreground-entry counts.
- Event-derived rolling 24-hour hourly timeline (no artificial equal distribution).
- Room/SQLite local daily snapshots and hourly snapshots.
- Saved daily usage history and sleep-estimate history.
- Weekly summary / top-app analytics.
- Quick search across available app usage records.
- Per-app opening-count notification rules with custom notification text.
- Notification-only alerts; no overlay permission or overlay window.
- Periodic WorkManager checks (minimum 15-minute cadence, subject to Android scheduling).
- Sleep estimate based on screen non-interactive + keyguard unlock + at least five minutes of foreground use after unlock, with a 65-minute no-unlock threshold.
- Local JSON backup and restore using Android's Storage Access Framework.
- Backup includes daily/hourly records, sleep records, theme, alert rules and local preferences.
- Privacy page and app About page with the integrated launcher logo.

## Required user permissions
1. Usage Access: required for app usage and system usage events.
2. Notifications: optional; required for Smart Alert notifications on Android 13+.

TIMELOG does not request Internet, Accessibility, overlay, device-admin, or QUERY_ALL_PACKAGES permissions.

## Important accuracy and platform limitations
- Android's UsageStats / UsageEvents are system-provided summaries and events. OEMs may delay, omit, or aggregate some events.
- App foreground-entry count is an approximation of app openings; Android may report activity transitions rather than a literal tap on the launcher icon.
- Hourly usage is reconstructed from foreground/background events. Incomplete event pairs and data gaps can affect totals.
- Sleep is a phone-inactivity estimate, not medical sleep tracking. The detector ignores network/background sync and notification delivery; it uses keyguard-unlock and subsequent foreground-use events as human-presence evidence.
- The 65-minute confirmation and five-minute morning-use check are evaluated through UsageEvents and WorkManager. WorkManager is not real-time and can be deferred by Doze, battery optimization, or OEM policies.
- Lifetime history begins only after TIMELOG has Usage Access and its periodic collector has started. It cannot recover older records that Android no longer exposes.
- Per-app notifications are checked periodically (15-minute minimum interval) and may be delayed by the operating system. Notification permission must be granted.
- Local restore replaces existing TIMELOG history and settings. Export a backup before restoring if current data must be retained.
- The current backup format is versioned JSON. Keep a copy of important backups outside the app's private storage.

## Privacy
No account, cloud sync, ads, analytics SDK, or network permission is configured. Usage data and backup files remain on the device unless the user explicitly exports a backup.
