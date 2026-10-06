# ML Kit resolves its internal components (e.g. SharedPrefManager) reflectively through its own
# component registry. R8 full mode otherwise optimizes them away and the document scanner crashes
# with a NullPointerException in GmsDocumentScanning.getClient().
-keep class com.google.mlkit.common.sdkinternal.** { *; }
-keep class com.google.mlkit.common.internal.** { *; }
