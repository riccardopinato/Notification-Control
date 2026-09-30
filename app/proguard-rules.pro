# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Preserva i numeri di riga e i nomi dei file sorgenti nei log di crash (Stacktrace Preservation)
-keepattributes SourceFile,LineNumberTable

# Mantieni le annotazioni di sistema e le firme dei tipi
-keepattributes *Annotation*,Signature,InnerClasses

# Impedisci l'offuscamento delle classi di preferenza e di tracciamento energetico
-keep class com.riccardopinato.notificationcontrol.utils.PremiumManager { *; }
-keep class com.riccardopinato.notificationcontrol.utils.BatterySavingsTracker { *; }
-keep class com.riccardopinato.notificationcontrol.utils.QuietHoursManager { *; }
-keep class com.riccardopinato.notificationcontrol.utils.ContactFilter { *; }
-keep class com.riccardopinato.notificationcontrol.services.ReminderManager { *; }
-keep class com.riccardopinato.notificationcontrol.data.AppSettings { *; }

# Proteggi i moduli del Google Play Billing da disfunzioni post-compilazione
-keep class com.android.billingclient.api.** { *; }

# Escludi le librerie Room e i relativi modelli se integrati in futuro
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

