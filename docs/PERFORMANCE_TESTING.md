Performance testing & profiling (scaffold)

This document describes how to profile the app for ANR, memory, and battery issues.

Local profiling (recommended):

1. Start an emulator or device and install the `debug` build.
2. Use Android Studio Profiler (CPU, Memory, Energy) to record traces.
3. For ANR reproduction, enable `adb logcat` and look for `ANR` keywords.

Recommended commands:

```bash
# Start adb logcat
adb logcat -c && adb logcat | tee logcat.txt

# Run a sample UI test (requires connected device/emulator)
./gradlew connectedAndroidTest
```

CI: consider using Firebase Test Lab for performance tests or a dedicated benchmarking runner.

This file is a scaffold; enable runs in CI only when ready to run emulator or cloud devices.
