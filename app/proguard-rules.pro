-keep class com.kryptsafe.** { *; }

-keep class org.videolan.** { *; }

# Argon2 JNI binds by class/method name and the AAR ships no consumer rules; renaming would crash setup/unlock in release.
-keep class com.lambdapioneer.argon2kt.** { *; }
