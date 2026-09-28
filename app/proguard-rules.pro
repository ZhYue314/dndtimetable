# Keep Shizuku API classes (used via reflection/binder)
-keep class dev.rikka.shizuku.api.** { *; }
-keep class moe.shizuku.api.** { *; }
-dontwarn dev.rikka.shizuku.**
-dontwarn moe.shizuku.**

# Keep the reflection-touched IAudioService method calls
-keepattributes *Annotation*
-dontwarn android.media.IAudioService
