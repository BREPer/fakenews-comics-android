# R8 规则集中在这里；Hilt / OkHttp / Coil / security-crypto 自带 consumer rules，不用重复抄。

# 注解与泛型签名是 Retrofit 的调用契约，R8 默认会精简掉
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# ---- kotlinx.serialization ----
# 插件生成的 $serializer / Companion 是合成类，没有直接引用，不 keep 会被当死代码删除
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    public static <1>$$serializer INSTANCE;
}

# ---- Retrofit ----
# 服务接口只被动态代理调用，代码里没有直接引用；不 keep 整段会被裁掉
-keep,allowobfuscation interface com.fakenews.comics.data.net.FakenewsApi
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# security-crypto 依赖的 Tink 只在类注解里引用 errorprone，运行时不需要这个类
-dontwarn com.google.errorprone.annotations.Immutable
