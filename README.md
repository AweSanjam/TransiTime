# Transi-Time

Wear OS app that shows live departures from the transit stop nearest to you, right on your wrist: every route at the stop, real-time countdowns for each direction, refreshed every 25 seconds while open. Three layouts with the same features (Classic, Pod, Radar), switched with a two-finger triple tap.

Data from [Transit](https://transitapp.com) (API key) with keyless [Transitous](https://transitous.org) as the fallback.

## State of this repository

- The original project folder was lost. `recovered-partial/` holds the Kotlin sources that could be recovered (`MainActivity.kt`, `PodUi.kt`, `RadarUi.kt`, `Controller.kt`, `Transit.kt`, `Transitous.kt`) and the original README (`README-original.md`). The Gradle build files, resources and manifest are missing, so it does **not** build yet.
- The last real build (1.0) is not published, because it had an API key built in. A new APK will be posted to [Releases](https://github.com/AweSanjam/TransiTime/releases) once the build is restored.

## API key

The Transit API key is read from `local.properties` (`transit.apiKey`) into `BuildConfig.TRANSIT_API_KEY`; `local.properties` is gitignored. Copy `local.properties.example` and add your own key.


## Open source

This project will be released as open source under the [MIT License](LICENSE). The project website is in `docs/` (ready for GitHub Pages: Settings → Pages → Deploy from branch `main`, folder `/docs`).
