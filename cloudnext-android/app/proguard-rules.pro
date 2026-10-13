# ============================================================
# CloudNext Android - R8 / ProGuard rules
# ============================================================

# 反射相关属性
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, EnclosingMethod

# ------------------------------------------------------------
# Kotlinx Serialization
# ------------------------------------------------------------
# 官方推荐规则：https://github.com/Kotlin/kotlinx.serialization#android
#
# 关键点：
# - @Serializable 类不能被 R8 重命名，否则运行时按类名查找 $$serializer 会失败
# - $$serializer 静态 INSTANCE 必须保留
# - Companion / serializer() 入口必须保留

-dontnote kotlinx.serialization.AnnotationsKt

# 保留所有 @Serializable 类的原名（含成员），覆盖项目自定义 + 第三方
-keep,includedescriptorclasses @kotlinx.serialization.Serializable class * { *; }

# 保留 $$serializer 内部类（一定有 INSTANCE 字段）
-keep,includedescriptorclasses class **$$serializer { *; }

# 保留 Companion 对象（kotlinx-serialization 经常通过它拿到 serializer）
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# kotlinx-serialization-json 自身
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ------------------------------------------------------------
# Retrofit / OkHttp
# ------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# Retrofit 通过 Proxy + 反射调用接口方法
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, RuntimeVisibleTypeAnnotations, AnnotationDefault

# Retrofit suspend 函数返回 Continuation
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# 保留所有 Retrofit 接口方法（项目自定义 API）
-keep interface com.x.cloudnext.android.**.api.** {
    @retrofit2.http.* <methods>;
}

# ------------------------------------------------------------
# Hilt / Dagger
# ------------------------------------------------------------
-keep class * extends androidx.lifecycle.ViewModel
-keep,allowobfuscation @dagger.hilt.android.lifecycle.HiltViewModel class *

# ------------------------------------------------------------
# Compose / Android 框架
# ------------------------------------------------------------
-keep class androidx.compose.runtime.** { *; }
-dontwarn org.slf4j.**
