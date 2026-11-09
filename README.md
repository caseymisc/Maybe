# FitDash

A personal Android dashboard for **Garmin Connect** and **Hevy** data.
Built with Kotlin and Jetpack Compose.

## Data sources

- **## Data sources

- **Garmin Connect, direct.** Covers Body Battery, sleep score and stages, Training Readiness, HRV
  status, stress and training status. Garmin has no public API for personal use, so FitDash signs in
  the way the Garmin Connect Android app does:
  1. You sign in on Garmin's own SSO page inside a WebView, so MFA works. FitDash never sees your
     password.
  2. The app catches the one-time service ticket and exchanges it at `diauth.garmin.com` for OAuth2
     tokens.
  3. Tokens are stored encrypted with an Android Keystore key and refreshed automatically.

  This mirrors the maintained [python-garminconnect](https://github.com/cyberjunky/python-garminconnect)
  library (0.3.x). If Garmin changes its sign-in again, update
  `data/garmin/GarminAuth.kt` and `GarminClient.kt` to match that library's latest release. If the
  default sign-in fails, the login screen offers the connect.garmin.com website sign-in as a
  fallback.
- **Health Connect.** Supplies steps, distance, calories, heart rate and activities, which the Garmin
  Connect app syncs there. In Garmin Connect, allow Health Connect under Connected Apps.
- **Hevy, official API.** Requires Hevy Pro. Create a key at https://hevy.com/settings?developer and
  paste it in the app's Settings screen. The key is stored only on the device.

:** steps, active energy, heart rate, resting HR, distance, last night's sleep with stages,
  and 7-day charts for steps, sleep and resting HR.
- **Training:** Hevy weekly volume (8 weeks), this week's totals, heaviest sets, recent workouts
  (tap one for set-by-set detail), plus Garmin activities from the last 14 days.
- **Settings:** Garmin sign-in and sign-out, Hevy API key, Health Connect shortcut, kg/lb.

## Build

Open the project in Android Studio, or run `./gradlew assembleDebug` and install
`app/build/outputs/apk/debug/app-debug.apk`. CI (`.github/workflows/android.yml`) runs the unit tests
and uploads the debug APK as an artifact on every push.

Requires Android 9+ (API 28). On Android 13 and lower, install the Health Connect app from the Play
Store. It is built into Android 14 and later.
