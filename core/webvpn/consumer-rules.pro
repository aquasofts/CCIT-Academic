# Retrofit reads service annotations and the generic continuation type of
# suspend functions at runtime. R8 full mode can otherwise reduce
# Continuation<ApiEnvelope<Model>> to Continuation<Object>, which makes the
# kotlinx-serialization converter impossible to create.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# Dynamic Retrofit proxies implement this interface at runtime. Keep the
# complete contract so its suspend-function generic signatures stay intact.
-keep interface edu.ccit.webvpn.core.webvpn.WebVpnApi { *; }

# The converter resolves serializers from these generic response types.
-keep,allowoptimization class edu.ccit.webvpn.core.webvpn.ApiEnvelope { *; }
-keep,allowoptimization class edu.ccit.webvpn.core.webvpn.**$$serializer { *; }
