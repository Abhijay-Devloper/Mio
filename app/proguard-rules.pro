# Strip verbose/debug/info logging in release builds so internal traces never ship to users.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Preserve Firestore data model classes annotated with @Keep
-keepclassmembers class com.example.data.model.** {
    *;
}
