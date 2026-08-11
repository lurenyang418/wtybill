# AGENTS.md

## Project overview

`wtybill` is a single-module native Android client for watching Douyu room `57321`.
It targets Android 13/API 33 and newer, uses Kotlin and Jetpack Compose, and is
intentionally focused on one room rather than multi-platform or multi-room features.

The repository currently has no Baseline Profile or Macrobenchmark module. Do not add
those tools or make cross-version, cross-vendor, or two-hour stability testing a V1
acceptance requirement.

## Repository layout

- `app/src/main/java/tech/wtybill/app/data/douyu/`: Douyu room APIs, dynamic-signature
  execution, stream resolution, models, and protocol errors.
- `app/src/main/java/tech/wtybill/app/danmaku/`: Douyu WebSocket lifecycle, binary
  packet codec, message parsing, buffering, and track allocation.
- `app/src/main/java/tech/wtybill/app/player/`: Media3 ExoPlayer, MediaSession service,
  controller connection, playback state, and recovery coordination.
- `app/src/main/java/tech/wtybill/app/MainActivity.kt`: activity lifecycle, Compose
  player screen, compact player controls, fullscreen, PiP, and Privacy & License page.
- `app/src/main/java/tech/wtybill/app/ui/room/`: Room ViewModel/state and danmaku
  overlay state/rendering.
- `app/src/main/java/tech/wtybill/app/settings/`: Preferences DataStore-backed settings.
- `app/src/main/java/tech/wtybill/app/config/AppConfig.kt`: fixed app and Douyu
  configuration, including the current room ID.
- `docs/`: project documentation, including Release signing instructions.
- `scripts/verify-crypto-js.mjs`: verifies the checked-in CryptoJS asset.

## Implementation boundaries

- Keep `AppConfig.ROOM_ID` at `57321` for normal development and commits. A different
  room may be used temporarily for live-stream testing, but restore `57321` afterward.
- Do not reintroduce the removed official-room-link action.
- `PlaybackService` is the owner of ExoPlayer and MediaSession. Activities and Compose
  UI communicate through `PlayerConnection`/`MediaController`; they must not create a
  second player instance.
- Keep the player-first UI compact: playback, mute, quality, CDN, refresh, PiP,
  fullscreen, danmaku, danmaku settings, and background-audio actions belong in the
  player overlay where practical. Fullscreen should show only the player rather than
  leaving the surrounding page scrollable.
- Privacy and permissions belong on the independent Privacy & License page, not in a
  playback control dialog.
- Keep network, dynamic JavaScript signing, parsing, and protocol work off the main
  thread. Reuse the shared OkHttp client and its derived clients.
- Treat remote URLs, dynamic scripts, signature output, room responses, and danmaku
  input as untrusted data. Preserve existing size, timeout, validation, and recovery
  limits when changing these paths.

## Build and verification

Use JDK 17 and the committed Gradle Wrapper when available:

```bash
./gradlew test :app:assembleDebug
node scripts/verify-crypto-js.mjs
./gradlew :app:assembleRelease :app:bundleRelease
```

For UI or lifecycle changes, also perform the relevant manual check on the connected
Android device when available: live playback, play/pause, mute, quality/CDN changes,
danmaku visibility/settings, PiP, fullscreen, rotation, background audio, and returning
from the independent Privacy & License page. If room `57321` is offline, report that
the live-stream check is blocked instead of treating an offline response as a code
failure.

Do not claim multi-version, multi-vendor, or long-duration stability coverage unless
the user explicitly adds that requirement later.

## Release signing

Release signing is opt-in. The ignored root `keystore.properties` may contain:

```properties
storeFile=keystore/wtybill-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

CI may provide the equivalent `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` variables. Gradle properties take
precedence over environment variables, which take precedence over the local file.
When any signing value is missing, Release artifacts remain unsigned; never add a
Debug-signing fallback. Never commit keystores, passwords, private keys, or other
release secrets.

## Files and Git hygiene

- `tmp/` is local working material and is ignored. Do not stage or commit anything from
  that directory.
- Do not commit `keystore.properties`, `keystore/`, `local.properties`, Gradle build
  outputs, or generated device artifacts.
- Keep dependency verification metadata in sync when changing dependencies.
- Prefer focused Kotlin/Compose changes that preserve the existing module boundaries.
- Before handing off changes, run `git diff --check`, inspect `git status`, and report
  any manual device or live-stream checks that could not be completed.
