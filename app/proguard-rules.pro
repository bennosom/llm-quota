# Project-specific R8/ProGuard keep rules.
# kotlinx-serialization generates serializers at compile time and ships its own
# consumer rules, so no additional keep rules are required for @Serializable classes.

# Tink (pulled in by androidx.security:security-crypto) references compile-only
# annotations that are not on the runtime classpath. They have no runtime effect.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy
