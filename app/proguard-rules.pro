# 客服小秘应用 - ProGuard优化规则

# 基础优化配置
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# 保持注解和签名信息
-keepattributes *Annotation*,Signature,InnerClasses,SourceFile,LineNumberTable

# 移除日志（生产环境）
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# 保留Room数据库相关
-keep class androidx.room.** { *; }
-keep class com.csbaby.kefu.data.model.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-keep @androidx.room.TypeConverter class *

# 保留Hilt依赖注入相关
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.Module
-keep class * extends dagger.android.AndroidInjector
-keep class * extends dagger.android.support.DaggerFragment

# 保留Gson序列化相关
-keep class com.google.gson.** { *; }
-keepattributes EnclosingMethod
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 保留Compose相关
-keep class androidx.compose.** { *; }
-keep class kotlin.Metadata { *; }
-keep class androidx.activity.compose.** { *; }

# 保留网络请求相关
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep class com.squareup.okhttp.** { *; }
-keep interface retrofit2.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# 保留JSON解析相关
-keep class com.google.code.gson.** { *; }
-keep class org.json.** { *; }

# 保留Timber日志相关
-keep class com.jakewharton.timber.** { *; }

# 保留WorkManager相关
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker

# 保留DataStore相关
-keep class androidx.datastore.** { *; }

# 保留Coil图片加载相关
-keep class io.coil_kt.** { *; }
-keep class coil.** { *; }
-dontwarn coil.**

# 保留导航组件相关
-keep class androidx.navigation.** { *; }

# 保留ViewModel相关
-keep class androidx.lifecycle.** { *; }

# 保留自定义Application类
-keep public class com.csbaby.kefu.KefuApplication {
    public <init>();
}

# 保留Activity和Service
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# 保留Parcelable实现
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# 保留Serializable类
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# 保留R类
-keep class **.R
-keep class **.R$* {
    <fields>;
}

# 保留BuildConfig
-keep class **.BuildConfig { *; }

# 通用库保留规则
-keep class androidx.core.** { *; }
-keep class androidx.fragment.** { *; }
-keep class androidx.appcompat.** { *; }
-keep class androidx.recyclerview.widget.** { *; }

# 忽略警告
-ignorewarnings
