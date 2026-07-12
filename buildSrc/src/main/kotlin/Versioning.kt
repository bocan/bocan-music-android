/**
 * Derives a monotonic Android versionCode from a semantic versionName.
 *
 * Formula: major * 10000 + minor * 100 + patch. Play requires versionCode to never
 * decrease across an upload, and Conventional Commits can bump any of the three parts,
 * so the code has to stay strictly increasing for any version that sorts higher. That
 * holds as long as minor and patch stay within [0, 99]: a 2.0.0 release (20000) always
 * outranks any 1.99.x hotfix (at most 19999). The caps are enforced rather than silently
 * wrapped, because a wrap would ship a lower code for a higher version and Play would
 * reject the upload with no obvious cause.
 *
 * A pre-release or build suffix (1.2.3-rc1, 1.2.3+7) contributes nothing to the code; the
 * three numeric core components are all that Play compares.
 */
fun versionCodeOf(versionName: String): Int {
    val core = versionName.trim().substringBefore('-').substringBefore('+')
    val parts = core.split('.')
    require(parts.size == 3) {
        "versionName must be MAJOR.MINOR.PATCH, got '$versionName'"
    }
    val (major, minor, patch) = parts.map { part ->
        part.toIntOrNull()
            ?: throw IllegalArgumentException("non-numeric version component in '$versionName'")
    }
    require(major >= 0) { "major must be >= 0, got $major" }
    require(minor in 0..99) { "minor must be in 0..99 to stay monotonic, got $minor" }
    require(patch in 0..99) { "patch must be in 0..99 to stay monotonic, got $patch" }
    return major * 10000 + minor * 100 + patch
}
