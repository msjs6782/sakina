# Keep line numbers so crash reports point at real code.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization: keep generated serializers of the app's settings model.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.sakina.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.sakina.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.sakina.app.**$$serializer { *; }
