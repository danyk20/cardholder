# ML Kit discovers its components through registrars listed in the manifest and instantiates them
# reflectively; R8 full mode would otherwise remove their no-argument constructors.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }

# ML Kit also resolves internal components (e.g. SharedPrefManager) reflectively through its own
# component registry; without these rules the document scanner crashes in GmsDocumentScanning.getClient().
-keep class com.google.mlkit.common.sdkinternal.** { *; }
-keep class com.google.mlkit.common.internal.** { *; }
