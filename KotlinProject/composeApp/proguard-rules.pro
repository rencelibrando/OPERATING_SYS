# Existing Ktor and SLF4J rules
-dontwarn io.ktor.**
-dontwarn org.slf4j.**

# Apache Commons and Logging warnings
-dontwarn org.apache.commons.logging.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.log.**
-dontwarn org.apache.commons.io.**

# XML and DOM related warnings
-dontwarn org.w3c.dom.**
-dontwarn javax.xml.**
-dontwarn org.xml.**

# Apache Batik SVG processing warnings
-dontwarn org.apache.batik.**
-dontwarn org.apache.xmlgraphics.**

# Sun/Oracle internal classes
-dontwarn sun.misc.**
-dontwarn sun.nio.**

# Other missing dependencies
-dontwarn org.fusesource.jansi.**
-dontwarn com.typesafe.config.**

# Keep all serialization classes
-keep class **$$serializer {
    *;
}

# Keep Supabase serialization classes specifically
-keep class io.github.jan.supabase.** { *; }
-keep class * implements kotlinx.serialization.KSerializer { *; }

# Keep serialization descriptors
-keepclassmembers class * {
    *** Companion;
}

# Existing serialization rules
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

-keepclassmembers class * {
    @kotlinx.serialization.Transient <fields>;
}

-keepclassmembers class * {
    @kotlin.jvm.JvmStatic ** Companion;
}

-keepclassmembers class **$$serializer {
    <methods>;
}

# Additional rules for reflection and dynamic class loading
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Keep kotlinx.serialization classes
-keep class kotlinx.serialization.** { *; }
-keep class kotlin.reflect.** { *; }

# Suppress additional library dependency warnings
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn java.awt.**
