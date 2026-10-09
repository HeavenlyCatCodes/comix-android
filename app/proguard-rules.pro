# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.comixreader.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.comixreader.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.comixreader.app.**$$serializer { *; }

# JavaScript bridge methods are called by name from the WebView.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-dontwarn org.jspecify.annotations.**
-dontwarn com.google.re2j.**
