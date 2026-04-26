# ProGuard/R8 configuration for AISuper Android App
# Aggressive minification with minimal keeps

# Keep app entry points
-keep class com.damn.aisuper.MainActivity {
    public <init>(...);
}

# Keep application classes that use reflection or are referenced in manifest
# (Application entry/activity is kept explicitly above; add any other manifest-referenced
# classes here if needed)

# Keep application data models and serializable classes
-keep class com.damn.aisuper.** implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep classes used by reflection in AISuper code
# NOTE: layout utility classes (ColorUtils, LayoutRenderer) are not used via reflection
# and can be safely minified/obfuscated. Prefer using Kotlin Serialization annotations
# (e.g. @kotlinx.serialization.Serializable) on data models to preserve them when needed.

# Serialization - minimal keep
# Keep generated serializer implementations and serializer accessors
-keep class * implements kotlinx.serialization.KSerializer { *; }
-keepclassmembers class * {
    public static kotlinx.serialization.KSerializer serializer();
}

# Keep classes annotated with kotlinx.serialization.Serializable (preserve structure for serializers)
-keep @kotlinx.serialization.Serializable class * { *; }

# Suppress warnings for JDK-only management classes referenced by some libraries (e.g. ktor)
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep R classes
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Preserve annotations used by framework/libraries
# Keep specific Compose stability marker annotation (annotation kept, allow obfuscation if safe)
# Note: avoid keeping all annotations or all interfaces which prevents minification.
-keep,allowobfuscation @interface androidx.compose.runtime.internal.StabilityInferred

# If your code needs specific interfaces kept for reflection, add them explicitly, e.g.:
# -keep interface com.damn.aisuper.somepackage.CallbackInterface { *; }

# Optimization settings
-optimizationpasses 5
-verbose

# Remove logging - more aggressive
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** println(...);
}

# Allow obfuscation and aggressive inlining
-allowaccessmodification
-mergeinterfacesaggressively

# Rename instead of remove
-repackageclasses 'com.damn.aisuper.obf'

# Preserve line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep exception names for crash reports
-keepattributes Exceptions

# Keep Kotlin metadata for reflection
-keepattributes Signature

# Keep InnerClasses and EnclosingMethod
-keepattributes InnerClasses,EnclosingMethod

# Don't obfuscate standard methods expected by reflection in Compose
-keep,allowobfuscation interface androidx.compose.runtime.internal.StabilityInferred

