# Project-specific R8 rules for the minified release build.
#
# Most of what a naive setup would list here is unnecessary in this app, and adding
# redundant rules just rots. The reasoning, verified against the code:
#
#  - kotlinx.serialization ships default consumer rules that keep the generated
#    serializers for every @Serializable class. Those extra rules are only needed for
#    classes with a *named* companion object (retrieved via getDeclaredClasses); a grep
#    of core/ and app/ finds none, and no code does reflective serializer lookup, so the
#    bundled rules are sufficient. If a named companion is ever added, add the
#    -if @kotlinx.serialization.Serializable block from the library README here.
#  - Room (androidx.room3) ships consumer rules that keep generated DAO and database
#    implementations. Entities are referenced directly, so nothing extra is required.
#  - Media3 ships consumer rules for the classes DefaultRenderersFactory loads.
#
# The one genuine hazard is the FFmpeg audio decoder. DefaultRenderersFactory is set to
# EXTENSION_RENDERER_MODE_PREFER (PlayerFactory.kt), which resolves the extension renderer
# reflectively by class name and then binds JNI native methods. If R8 renames or strips
# those classes or their native-registered members, the extension silently disappears (no
# FFmpeg formats) or fails with a LinkageError at first decode. Keep them explicitly. The
# native .so and the Media3 version are pinned together in libs.versions.toml for the same
# reason, so this is the belt to that suspenders.

-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keep class org.jellyfin.media3.** { *; }

# Native methods are bound by JNI signature; never rename or remove them.
-keepclasseswithmembernames class * {
    native <methods>;
}

# The InnerClasses attribute is cheap to keep and lets any future serializable class with
# a named companion resolve without a surprise release-only crash.
-keepattributes InnerClasses
