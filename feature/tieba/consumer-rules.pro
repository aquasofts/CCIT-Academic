# Retrofit/Gson support responses and Wire read services use annotations/generic signatures.
-keepattributes Signature,*Annotation*
-keepclassmembers class edu.ccit.webvpn.feature.tieba.network.** {
    <fields>;
}
-keep interface edu.ccit.webvpn.feature.tieba.network.TiebaSupportApi { *; }
-keep interface edu.ccit.webvpn.feature.tieba.network.TiebaReadApi { *; }
-keep class com.huanchengfly.tieba.post.api.models.protos.** { *; }
