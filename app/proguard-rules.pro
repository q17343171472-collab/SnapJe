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
  
"########## GalleryX ProGuard Rules ##########"  
  
"# Keep data classes"  
"-keep class com.varun.galleryx.data.** { *; }"  
"-keep class com.varun.galleryx.domain.** { *; }"  
  
"# Keep ViewModels"  
"-keep class com.varun.galleryx.ui.**ViewModel { *; }"  
  
"# Keep Hilt generated classes"  
"-keep class dagger.hilt.** { *; }"  
"-keep class javax.inject.** { *; }"  
"-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }"  
  
"# Keep Compose"  
"-keep class androidx.compose.** { *; }"  
  
"# Keep Coil"  
"-keep class coil.** { *; }"  
  
"# Keep Kotlin Coroutines"  
"-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}"  
"-keepnames class kotlinx.coroutines.CoroutineScope {}"  
  
"# Keep model classes for serialization"  
"-keepclassmembers class * { *; }" 
