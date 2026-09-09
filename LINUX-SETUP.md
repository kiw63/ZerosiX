# Real Linux mode

Linux mode is intentionally real: ZerosiX launches a proot process against a real extracted Linux root filesystem.

You need:
1. A compatible Android/ARM proot executable named `proot`.
2. An extracted Linux rootfs containing at minimum `bin/sh`.
3. Import both from the LINUX screen.

ZerosiX stores them under its private app files directory and launches:

`proot -0 -r <rootfs> -b <app-files>:/host /bin/sh -l`

This is rootless Linux userland. It shares the Android kernel; it is not a VM and does not provide Android root privileges.
