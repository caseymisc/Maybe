# FitDash

A personal Android dashboard for **Garmin** (via Android Health Connect) and **Hevy** data.
Built with Kotlin and Jetpack Compose.

## Data sources

- **Garmin → Health Connect.** The Garmin Connect app syncs steps, distance, calories, heart rate,
  sleep and activities into Health Connect, and FitDash reads them from there. In Garmin Connect,
  allow Health Connect under Connected Apps. Garmin-only metrics (Body Battery, sleep score, stress,
  Training Readiness) are not in Health Connect, so they are not shown.
- **Hevy → official API.** Requires Hevy Pro. Create a key at https://hevy.com/settings?developer and
  paste it in the app's Settings screen. The key is stored only on the device.

## Screens

- **Today:** steps, active energy, heart rate, resting HR, distance, last night's sleep with stages,
  and 7-day charts for steps, sleep and resting HR.
- **Training:** Hevy weekly volume (8 weeks), this week's totals, heaviest sets, recent workouts
  (tap one for set-by-set detail), plus Garmin activities from the last 14 days.
- **Settings:** Hevy API key, Health Connect shortcut, kg/lb.

## Build

Open the project in Android Studio, or run `./gradlew assembleDebug` and install
`app/build/outputs/apk/debug/app-debug.apk`. CI (`.github/workflows/android.yml`) runs the unit tests
and uploads the debug APK as an artifact on every push.

Requires Android 9+ (API 28). On Android 13 and lower, install the Health Connect app from the Play
Store. It is built into Android 14 and later.
