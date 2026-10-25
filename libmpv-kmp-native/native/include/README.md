# Vendored mpv headers

`mpv/client.h` and `mpv/stream_cb.h` come from the mpv release that `v_mpv` in `buildscripts/include/depinfo.sh` names.
The desktop JNI library and the Kotlin/Native binding compile against these copies, so the API never depends on the mpv that a machine has installed.

When `v_mpv` changes, copy both files again from `buildscripts/deps/mpv/include/mpv/` after `buildscripts/download-deps.sh`.
The `mpvHeaders` check fails until the copies match that checkout.
