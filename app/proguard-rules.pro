# Add project specific ProGuard rules here.
-keepattributes Signature
-keepattributes *Annotation*

# Retrofit
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.rustypastechat.**$$serializer { *; }
-keepclassmembers class com.rustypastechat.** {
    *** Companion;
}
-keepclasseswithmembers class com.rustypastechat.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Coil
-keep class coil.** { *; }

# Hilt / Dagger
-dontwarn dagger.**
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Google Tink (used transitively by EncryptedSharedPreferences/EncryptedFile in
# security/SecurePreferences + VaultCrypto) references errorprone's compile-time-only
# annotations, which aren't on the runtime classpath and don't need to be.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi

# JSch (com.github.mwiede fork) - SFTP backup.
#
# Every cipher, MAC, key-exchange, signature and compression implementation is
# resolved by fully-qualified class NAME out of the string map in JSch's
# default config; nothing references them statically. R8 therefore removed all
# of them. Measured on this build before this rule existed: the jar ships 111
# classes under com/jcraft/jsch/{jce,bc,juz}/ and the release mapping contained
# ZERO of them, while com.jcraft.jsch.JSch itself survived as z3.l. The result
# is SFTP that works in debug and fails in release with an algorithm
# negotiation error - a release-only break that no debug test can catch.
-keep class com.jcraft.jsch.** { *; }
-keepclassmembers class com.jcraft.jsch.** { *; }

# Keeping the whole package also keeps JSch's desktop-only integrations, which
# reference libraries that do not exist on Android and are never reached here:
# the Windows Pageant agent connector (JNA), the unix-socket agent connector,
# Kerberos/GSS auth, and the log4j2/slf4j logger adapters. JSch picks a logger
# and an agent connector at runtime and falls back when the class is absent, so
# the dangling references are inert - they only have to stop failing the build.
-dontwarn org.bouncycastle.**
-dontwarn com.sun.jna.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.ietf.jgss.**
-dontwarn org.newsclub.net.unix.**
-dontwarn org.slf4j.**
