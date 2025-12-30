# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep Kotlin metadata, which is used by libraries like Moshi and Retrofit for reflection.
-keepattributes Signature,InnerClasses,EnclosingMethod,Kotlin*

# Keep Parcelize annotated classes and their CREATOR fields.
-keep class * implements android.os.Parcelable {
  public static final ** CREATOR;
}
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Keep Coroutines stuff
-keepclassmembers class kotlinx.coroutines.internal.MainDispatcherFactory {
    public static final kotlinx.coroutines.MainCoroutineDispatcher *;
}

#------------------ Room ------------------
# Keep all Room @Entity data classes and their members.
-keep @androidx.room.Entity public class * { *; }

#------------------ Koin ------------------
# Keep all ViewModel classes and their constructors for Koin's dependency injection.
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}
# Keep classes instantiated by Koin.
-keep class com.lonx.lyrico.di.AppModuleKt* { *; }
-keep class com.lonx.lyrico.utils.** { *; }
-keep class com.lonx.lyrico.data.repository.** { *; }
-keep class com.lonx.lyrics.source.kg.** { *; }
-keep class com.lonx.lyrics.source.qm.** { *; }
-keep class com.lonx.lyrico.data.** { *; }

#------------------ Compose Destinations ------------------
# Keep generated classes from Compose Destinations for navigation.
-keep class com.ramcosta.composedestinations.generated.** { *; }

#------------------ Coil ------------------
# Keep custom Fetcher for Coil to load audio file covers.
-keep class com.lonx.lyrico.utils.coil.AudioCoverFetcher* {
    <init>(...);
    *;
}
-keep class coil.RealImageLoader* {
    <init>(...);
}

#------------------ Project Specific Models & UI ------------------
# Keep data model classes to prevent their properties from being removed.
-keep public class com.lonx.lyrico.data.model.** { *; }
-keep public class com.lonx.audiotag.model.** { *; }
-keep public class com.lonx.lyrics.model.** { *; }

# Keep Composable functions and related application classes that might be called via reflection.
-keepclassmembers public class com.lonx.lyrico.screens.** {
    public static ** *(**);
}
-keepclassmembers public class com.lonx.lyrico.LyricoAppKt {
    public static ** *(**);
}
-keepclassmembers public class com.lonx.lyrico.App {
    <init>(...);
}

# Keep all methods and fields of classes annotated with @Composable.
# This is a broad rule but often necessary to prevent runtime crashes in Compose with Proguard.
-keepclasseswithmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
