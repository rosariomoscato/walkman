# Regole ProGuard/R8 di WalkTape.
# La prima versione usa minify disattivato; le regole arrivano con la build di rilascio (wave 12).

# W09 logical player routes the PUBLIC Media3 API by method name. Preserve these
# interface methods when minification is enabled; no Media3 private internals are used.
-keep interface androidx.media3.common.Player { *; }
-keep interface androidx.media3.common.Player$Listener { *; }
-keep interface androidx.media3.exoplayer.ExoPlayer { *; }
# The overlap promotion receipt stores these field names in private Room JSON.
-keep class com.walktape.app.core.db.ListeningStateEntity { <fields>; }
