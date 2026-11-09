# ==============================================================================
# R8 / ProGuard Configuration for PDF ALL IN ONE Pro+Ai Release Builds
# ==============================================================================

# 1. Strip all debug, verbose, info, warn, and error android.util.Log calls
#    and Throwable.printStackTrace() in release builds so no file names or
#    document contents can ever be written to system logcat.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

-assumenosideeffects class java.lang.Throwable {
    public void printStackTrace();
}

# 2. Keep source file line numbers for crash stack traces while obfuscating file names
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*

# 3. PdfBox-Android & BouncyCastle Cryptographic Providers (AES-256 / PKIX)
#    Scoped to JCE/CMS/ASN1/PKIX packages used by PDFBox so R8 strips unused BouncyCastle protocols
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
-keep class org.bouncycastle.jce.provider.** { *; }
-keep class org.bouncycastle.jcajce.** { *; }
-keep class org.bouncycastle.crypto.** { *; }
-keep class org.bouncycastle.asn1.** { *; }
-keep class org.bouncycastle.cms.** { *; }
-keep class org.bouncycastle.cert.** { *; }
-keep class org.bouncycastle.operator.** { *; }
-dontwarn org.bouncycastle.**

# 4. Direct APK In-App Update Checker Models & PackageInstaller Receiver
-keep class com.example.update.** { *; }

# 5. Room Database Entities, DAOs, and Database Implementation
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# 6. Optional Javax / Security Warnings
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.openjsse.**

# 7. Google ML Kit Text Recognition (Latin OCR) & Document Scanner
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
-dontwarn com.google.mlkit.**

# 8. Firebase AI Logic & Firebase App Check
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
