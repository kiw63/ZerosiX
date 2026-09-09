# ZerosiX Android

Native Android HUD/terminal project by **@Marvel_sh**.

## Real features in this source
- PTY-backed interactive Android shell using Termux terminal components.
- Three terminal tabs with independent sessions.
- CPU/RAM/storage/battery/network telemetry from Android/Linux procfs APIs.
- Realtime CPU and network graphs.
- Real `/proc` process inspection where the Android build permits it.
- Real `/proc/net/tcp` and `/proc/net/udp` socket inspection where permitted.
- Android document picker and app-sandbox file interface.
- Real Tor Android service integration using Guardian Project tor-android.
- Rootless Linux userland support using a real proot executable + real extracted rootfs.
- Startup animation and original local sound effects.
- Portrait immersive HUD; terminal pinch zoom is disabled.

## Important Android security boundaries
ZerosiX does **not** fake privileges. Android's sandbox/scoped-storage rules still apply. Root access is only available on a rooted device after the OS/user grants it. Linux mode is a real userland, not a simulated terminal, but it requires a compatible proot binary and a real rootfs.

The terminal can be used for normal development, administration and authorized security labs/CTFs. Do not use it to access systems without permission.

## Build
Open this project in AndroidIDE or another Android Gradle environment and run `assembleDebug`.

The project intentionally does not ship a Linux rootfs or proprietary third-party payloads. Import a compatible proot executable and extracted Linux rootfs through the in-app Linux setup screen.
