# The application uses only platform APIs and org.json.
-renamesourcefileattribute SourceFile
-keepattributes Signature
-adaptclassstrings
-repackageclasses 'h'
-allowaccessmodification
-dontusemixedcaseclassnames

# Android components are referenced by the manifest. No broad business-model keeps are needed.
-keep class com.openzen.heyboxcommunity.MainActivity { <init>(); }
-keep class com.openzen.heyboxcommunity.ImageViewerActivity { <init>(); }
