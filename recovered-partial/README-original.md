# Transi-Time

Wear OS app that shows live departures from the transit stop nearest to you, right on your wrist.
It finds your location, picks the closest stop, and lists every route there with real-time
countdowns for each direction. The list refreshes every 25 seconds while the app is open.
Location and network are used only while the app is on screen.

It has three layouts with the same features: **Classic**, **Pod** and **Radar**. There's no
setting for them. You switch between them with a hidden gesture.

<p align="center">
  <img src="docs/classic-home.png" width="200" alt="Classic layout">
  <img src="docs/pod-home.png" width="200" alt="Pod layout">
  <img src="docs/radar-home.png" width="200" alt="Radar layout">
</p>

## Features

- Nearest stop with distance, coordinates and last sync time.
- Every route at the stop with the next departures in each direction. A pulsing dot marks
  real-time times.
- SkyTrain and other rail lines in their own blue, apart from buses.
- Pinned routes stay at the top, even when you switch data source.
- Full departure list per direction, with countdowns and clock times in 12 h or 24 h
  (following the watch).
- Data from [Transit](https://transitapp.com) (API key), with keyless
  [Transitous](https://transitous.org) as the fallback. Pick Auto, Transit or Transitous in
  Settings.
- A radar sweep plays every time the app opens while the first sync runs. When it finishes,
  the list on the current page scrolls back to the top.

## Switching layouts

**Triple-tap with two fingers anywhere.** The app shows a toast, "Switching to Radar mode",
restarts, and opens in the next layout: Classic → Pod → Radar → Classic. It remembers the
layout you chose.

<p align="center"><img src="docs/mode-switch.gif" width="240" alt="Switching layouts with a two-finger triple tap"></p>

## Classic

Material 3 Expressive cards in a scrolling list.

<p align="center">
  <img src="docs/classic.gif" width="240" alt="Classic layout in use">
  <img src="docs/classic-times.png" width="200" alt="Classic times page">
</p>

| Do this | What it does |
|---|---|
| Scroll or turn the crown | Move through the stops and routes |
| Swipe a card left or right | Switch that route's direction |
| Tap a card | Open all departures for that direction |
| Hold a card | Pin or unpin the route |
| Pin button (times page) | Pin or unpin the route |
| Refresh button | Sync now |
| Settings button | Choose the data source |
| Swipe right | Go back, or close the app from the main list |

## Pod

An iPod-style menu in radar green on black. **Nothing on screen is a button.** The whole
touchscreen works like a click wheel.

<p align="center">
  <img src="docs/pod.gif" width="240" alt="Pod layout in use">
  <img src="docs/pod-route.png" width="200" alt="Pod route page">
  <img src="docs/pod-times.png" width="200" alt="Pod times page">
</p>

| Do this | What it does |
|---|---|
| Drag up or down, or turn the crown | Move the highlight |
| Tap anywhere | Open the highlighted row |
| Hold anywhere | Pin or unpin the highlighted route |
| One-finger swipe right | Go back, or close the app from the top menu |

- **Main menu:** the stop at the top (opens stop details), then one large row per route. Each
  row shows the route number, a ★ if pinned, and one line per direction with the next two
  times, so you can read everything at a glance. "Sync now" and "Settings" are at the end.
- **Route page:** **Pin / Unpin** is the first row. Under it, each direction shows its stop and
  next three times.
- **Times page:** every upcoming departure with its clock time.

## Radar

The opening radar keeps running. Each route is a blip at a random bearing. **Pinned routes sit
inside the dashed ring near the centre, the rest further out.** Each blip shows its route
number, a ring in the route's colour, and the next departure. Blips glow as the beam passes.

<p align="center">
  <img src="docs/radar.gif" width="240" alt="Radar layout in use">
  <img src="docs/radar-zoom.png" width="200" alt="Radar zoomed in">
  <img src="docs/radar-route.png" width="200" alt="Radar route page">
</p>

| Do this | What it does |
|---|---|
| Tap a blip | Open that route |
| Hold a blip | Pin or unpin it |
| Tap the centre dot | Sync now; a ping ripples out while it runs |
| Tap the gear at 6 o'clock | Settings |
| Turn the crown or pinch | Zoom from 0.7× to 3× |
| Drag while zoomed in | Look around |
| Tap empty space while zoomed | Zoom back to 1× |
| Swipe right (not zoomed) | Close the app |

At 1.6× and above, each blip also shows every direction with its next departure.

On a **route page**, swipe left or right to switch direction; the dots at the bottom show
which one you're on. Hold anywhere to pin, and swipe right on the first direction to go back
to the radar.

## Returning to the top

Each time the app opens, the radar sweep plays. When it ends, the page you left off on
scrolls back to the top, in every layout.

<p align="center"><img src="docs/reopen-top.gif" width="240" alt="List back at the top after reopening"></p>

## Building

Requires JDK 17 (with `jlink`) and the Android SDK (compileSdk 35). Min SDK 30 (Wear OS 3+).

```sh
# Optional: Transit API key (free at https://transitapp.com/apis)
echo "transit.apiKey=YOUR_KEY" >> local.properties

./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Without a key, the app uses Transitous only. The release build is signed with the local debug
key, for sideloading.

Stack: Kotlin 2.1, Jetpack Compose for Wear OS (Material 3 1.5), AGP 8.9, Gradle 8.11.

## Code map

| File | What's in it |
|---|---|
| `MainActivity.kt` | Activity, opening radar, layout switch gesture, scroll-to-top rule, Classic layout |
| `PodUi.kt` | Pod layout: menu stack, click-wheel input, title bar |
| `RadarUi.kt` | Radar layout: blip placement, zoom/pan, route pager |
| `Controller.kt` | Location, sync loop, preferences (pins, data source, layout) |
| `Transit.kt` / `Transitous.kt` | Data sources |

## Credits

Departure data powered by [Transit](https://transitapp.com). Keyless fallback by
[Transitous](https://transitous.org) (community-run, open data).
