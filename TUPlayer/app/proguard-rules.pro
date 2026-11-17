# libmpv's JNI bridge looks up MPVLib members by name from native code.
-keep class dev.jdtech.mpv.MPVLib { *; }
-keep interface dev.jdtech.mpv.MPVLib$EventObserver { *; }
-keep interface dev.jdtech.mpv.MPVLib$LogObserver { *; }
