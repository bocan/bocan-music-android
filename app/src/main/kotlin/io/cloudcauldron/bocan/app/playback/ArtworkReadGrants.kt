package io.cloudcauldron.bocan.app.playback

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.observability.LogCategory
import io.cloudcauldron.bocan.playback.ArtworkAccess

/**
 * The production [ArtworkAccess]: one prefix grant on the artwork root makes
 * every `content://<pkg>.fileprovider/artwork/<hash>` Uri readable by the target
 * package, so Android Auto can load browse icons and now-playing art from its
 * own process. A grant lasts until reboot and is simply re-issued on the next
 * connect; only the artwork subtree is ever shared, never tracks or episodes.
 *
 * The grant call is injectable so tests can observe the exact Uri and flags
 * without the platform's permission machinery.
 */
class ArtworkReadGrants(
    context: Context,
    private val grant: (toPackage: String, uri: Uri, modeFlags: Int) -> Unit = context::grantUriPermission
) : ArtworkAccess {
    private val log = AppLog.forCategory(LogCategory.Playback)

    // The authority matches the manifest's ${applicationId}.fileprovider entry; the
    // "artwork" segment is the path name FileProvider assigns in file_paths.xml.
    private val artworkRoot: Uri = Uri.Builder()
        .scheme(ContentResolver.SCHEME_CONTENT)
        .authority("${context.packageName}.fileprovider")
        .appendPath("artwork")
        .build()

    override fun grantReadTo(packageName: String) {
        try {
            grant(packageName, artworkRoot, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        } catch (refused: SecurityException) {
            log.warning("artwork.grant.failed", mapOf("package" to packageName, "error" to refused.toString()))
        } catch (refused: IllegalArgumentException) {
            log.warning("artwork.grant.failed", mapOf("package" to packageName, "error" to refused.toString()))
        }
    }
}
