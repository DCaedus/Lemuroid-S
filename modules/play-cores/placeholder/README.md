# Placeholder library

Google Play requires every module of the app to contain native libraries for the same processor types
(ABIs). Some cores aren't built for all of them (Azahar is arm64-only, melonDS DS has no 32-bit x86 build),
so their Play module gets this empty library (`libplaceholder_<core>.so`) for the missing types. Devices of
those types simply don't have that core, and the app doesn't offer the system there
(`supportedOnlyArchitectures` in GameSystem.kt).

Built with NDK 29 from a one-line C file:

    echo 'void lemuroid_s_placeholder(void) {}' > p.c
    clang --target=<arch>-linux-android23 -shared -Wl,-z,max-page-size=16384 -s -o libcore_placeholder.so p.c
