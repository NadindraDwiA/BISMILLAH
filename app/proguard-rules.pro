# ONNX Runtime Mobile
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
# DataStore / protobuf / kotlinx-serialization runtime
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**
-keep class com.google.protobuf.** { *; }
# Keep model + service entry points (referenced from Manifest / reflection-free but explicit)
-keep class com.example.bismillah.data.model.** { *; }
-keep class com.example.bismillah.service.ScreenTranslatorService { *; }
