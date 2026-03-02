# InterSego

Bus timetable app for the metropolitan area of Segovia, Spain. Check schedules for Linecar interurban bus routes (M1-M8), see live countdowns to the next departure, and browse timetables — all with full offline support.

## Features

- **Route browser** — View all metropolitan bus routes (M4 and M6 currently supported, more coming soon)
- **Stop-by-stop display** — Visual route line showing every stop, with direction toggle
- **Next departure countdown** — Live timer to the next bus, with a map showing the stop location
- **Offline-first** — Timetables are cached locally so the app works without an internet connection
- **Auto-updating schedules** — PDFs are downloaded from Linecar's website and re-fetched automatically when they change

## Platforms

| Platform | Status | Tech Stack |
|----------|--------|------------|
| Android  | Available | Kotlin, Jetpack Compose, Material 3 |
| iOS      | Available | Swift, SwiftUI |

## Building

### Android

```bash
cd android
./gradlew assembleDebug
./gradlew installDebug
```

Requires Android SDK with API 34+.

### iOS

Open `iOS/InterSego.xcodeproj` in Xcode and build. Deployment target: iOS 17.0.

## How It Works

InterSego downloads official PDF timetables published by [Linecar](https://www.linecar.es/metropolitano/segovia/) and parses them into structured data. A three-tier caching system (memory, JSON, PDF) keeps the app fast and responsive even without network access. If a PDF link goes stale, the app automatically re-scrapes the Linecar website for the updated URL.

## Screenshots

*Coming soon*

## Privacy

See [PRIVACY_POLICY.md](PRIVACY_POLICY.md).

## License

This project is licensed under the [GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.en.html).

Copyright (C) 2025 Bruno Follon ([@bFollon](https://github.com/bFollon))
