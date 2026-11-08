# Disable ProGuard optimization to prevent JVM verifier "Bad type on operand stack" / VerifyErrors
-dontoptimize

# Keep all runtime visible/invisible annotations and signature attributes
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,RuntimeVisibleParameterAnnotations,RuntimeInvisibleParameterAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# Keep Ktor CIO Client & Service Loader files
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Keep Kotlinx Coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Keep Dokar QuickJS and JNI bindings completely intact
-keep class com.dokar.quickjs.** { *; }
-dontwarn com.dokar.quickjs.**

# Keep Kotlinx Serialization and its synthetic helpers
-keep class **$annotationImpl$** { *; }
-keep class **$Companion$** { *; }
-keep class **$$serializer { *; }

-keepclassmembers class * {
    *** Companion;
}
-keep class * implements kotlinx.serialization.KSerializer { *; }
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <methods>;
}
-keep class * implements kotlinx.serialization.internal.GeneratedSerializer { *; }
-keepclassmembers class * {
    *** write$Self(...);
}

# Keep our own application and shared library classes to avoid any reflection/JS-bridge issues
-keep class com.damn.aisuper.** { *; }
-dontwarn com.damn.aisuper.**