# Error Prone annotations reference the JDK compiler model, which is not part of Android runtime.
-dontwarn javax.lang.model.element.Modifier

# AndroidJUnitRunner calls this before tests start on API 26. Keep the concrete
# implementation in the minified acceptance test APK instead of a reference-only stub.
-keep class androidx.tracing.** { *; }
