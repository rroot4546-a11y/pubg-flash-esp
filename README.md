# PUBG Flash ESP — overlay APK

Two engines, one APK:

1. **Vision ESP (no root)** — captures the game's own pixels with `MediaProjection`, runs the bundled
   on-device ML Kit person detector, and draws boxes over detected players. No `su`, no `/proc`, no
   game-memory reads.
2. **Memory ESP (root)** — a root C helper reads `/proc/<pid>/mem`, resolves the UE4 world and actor
   array, and sends a packed frame to the overlay. Read-only; the game process is never modified.

Pick the engine in the app. Vision is the default and works on a stock, unrooted phone.

## What ships

- `app/src/main/java/com/ofx/pubgesp/VisionOverlayService.java` — MediaProjection capture, ML Kit
  on-device person detection, box projection and lifecycle.
- `app/src/main/java/com/ofx/pubgesp/VisionOverlayView.java` — transparent full-screen box renderer.
- `native/esphelper.c` — root-side C reader, AF_UNIX protocol, ELF base + GWorld + actor array walk.
- `app/src/main/java/com/ofx/pubgesp/` — Java client, overlay renderer, profile/offset editor.
- `.github/workflows/build.yml` — builds helper with NDK, APK with Gradle, uploads artifact and release.

## Build locally

```bash
export ANDROID_SDK_ROOT=/path/to/Android/Sdk
export ANDROID_NDK_HOME=$ANDROID_SDK_ROOT/ndk/25.2.9519653
./scripts/build-helper.sh
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions does the same on every push to `main`; grab the APK from the workflow artifact or
the `v*` release.

## Install and run — no root

1. Install the APK on an ARM64 Android 7+ device.
2. Open the app → leave **Vision ESP (no root)** selected.
3. Grant "display over other apps" when prompted.
4. Tap **START OVERLAY** → accept the Android screen-capture consent dialog.
5. Start PUBG Flash. Red boxes track detected people in the game image.
6. Lower-end phones can be slower; the capture is downscaled to a 720px long edge before detection.

Nothing is written to the game. The detector sees the same pixels the player sees.

## Install and run — root memory mode

1. Device must be rooted with Magisk; `su` must grant the app root silently.
2. Select **Memory ESP (root)**, grant overlay permission, tap **START OVERLAY**.
3. Start PUBG Flash. The overlay draws boxes, names, health bars and distances over the game.
4. Use **EDIT OFFSETS** if a game update shifts a pointer.

Defaults target **PUBG FLASH 4.6.1 x64**. Public globals used:

```
GName        base + 0xF06B820 (+0x110)
GWorld       base + 0xF3B85F8 (-0x20 deref, +0x30)
VMatrix      base + 0xF5D8170 (+0xC0)
VWorld       base + 0xF33F5F0
GUObject     base + 0xF33C900
GNativeApp   base + 0xF06B3A8
Actors       ULevel + 0xA0
```

`UWorld::PersistentLevel` uses the stock UE4 x64 `0x30`, `AActor::RootComponent` the stock x64
`0x168`. Player member offsets (health/team/name) live in `OffsetProfile.java` and are editable in
the app; update them from a dumped SDK when the game version changes.

## Memory protocol

One AF_UNIX socket at `/data/local/tmp/ofx_esp.sock`:

| byte | request | response |
|---|---|---|
| `H` | — | `H` + pid (u32) + libUE4 base (u64) + status |
| `C` | 140-byte packed config | 1 byte status |
| `F` | — | `F` + status + count + view matrix + N×64-byte entries |
| `D` | address (u64) + length (u32) | 1 byte status; dump at `/sdcard/Download/esp_dump.bin` |
| `X` | — | closes socket |

`app/src/main/java/com/ofx/pubgesp/EspProtocol.java` and `native/esphelper.c` must stay in sync.

## Notes

- Root memory helper is ARM64 (`aarch64-linux-android24`); vision mode is pure Java + ML Kit.
- ML Kit bundles native pipelines for all ABIs, so the debug APK is ~48 MB.
- Anticheat can still detect screen capture or overlays; use a test account.
