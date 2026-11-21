# PUBG Flash ESP — external overlay APK

Root-only external ESP for **PUBG MOBILE FLASH** (`com.tencent.igfit`). The game process is never
modified: a root helper reads `/proc/<pid>/mem`, resolves the UE4 world and actor array, and sends a
packed frame to a normal Android overlay view.

## What ships

- `native/esphelper.c` — root-side C reader, AF_UNIX protocol, ELF base + GWorld + actor array walk.
- `app/src/main/java/com/ofx/pubgesp/` — Java client, overlay renderer, profile/offset editor.
- `.github/workflows/build.yml` — builds the helper with NDK, the APK with Gradle, uploads the artifact.

## Build locally

```bash
export ANDROID_SDK_ROOT=/path/to/Android/Sdk
export ANDROID_NDK_HOME=$ANDROID_SDK_ROOT/ndk/25.2.9519653
./scripts/build-helper.sh
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions does the same on every push to `main`; grab the APK from the workflow artifact.

## Install and run

1. Install on a rooted (Magisk) ARM64 device. `su` must grant the app root silently.
2. Open the app → **START OVERLAY** → grant "display over other apps" when prompted.
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

## Protocol

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

- Requires ARM64 (helper is `aarch64-linux-android24`).
- Reads only. Nothing is written into the game process.
- Anticheat can still detect the overlay/root; use a test account.
