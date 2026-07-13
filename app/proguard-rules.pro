# The application uses only platform APIs and org.json.
-renamesourcefileattribute SourceFile
-keepattributes Signature
-adaptclassstrings
-repackageclasses 'h'
-allowaccessmodification
-dontusemixedcaseclassnames

# Android components are referenced by the manifest. No broad business-model keeps are needed.
-keep class com.ronan.heyboxlite.MainActivity { <init>(); }
-keep class com.ronan.heyboxlite.ImageViewerActivity { <init>(); }

# Native methods are resolved by exact Java class and method names.
-keep class com.graphice.shaderar.ShaderManager { *; }
-keep class com.max.xiaoheihe.utils.NDKTools { *; }
-keep class com.nmmedit.protect.NativeUtil { *; }
-keep class com.meituan.robust.** { *; }
-keep class com.max.hbcommon.** { *; }
-keep class com.max.hbutils.** { *; }
-keep class com.max.heybox.hblog.** { *; }
-keep class com.max.xiaoheihe.app.HeyBoxApplication { *; }
-keep class com.max.xiaoheihe.bean.account.** { *; }
-keep class com.max.xiaoheihe.module.account.accelworld.** { *; }
-keep class com.max.xiaoheihe.utils.** { *; }
-keep class com.max.xiaoheihe.router.serviceimpl.k { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class com.ronan.heyboxlite.NativeLibraryLoader { *; }
