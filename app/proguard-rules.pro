# kotlinx.serialization and SQLDelight ship their own consumer rules; these cover our DTOs.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class one.calypsocloud.ezamp.data.** {
    *** Companion;
}
-keepclasseswithmembers class one.calypsocloud.ezamp.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn org.slf4j.**
