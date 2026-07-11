package io.cloudcauldron.bocan.app.podcasts

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import io.cloudcauldron.bocan.app.R

/**
 * Show notes rendered from sanitized HTML. The untrusted feed HTML is stripped of scripts,
 * styles, event handlers, and javascript: URLs by [ShowNotesSanitizer], then rendered with
 * Html.fromHtml into a TextView (never a WebView). https links open in the system browser;
 * anything insecure is confirmed first, per [ShowNotesLinks].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowNotesSheet(title: String, descriptionHtml: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var pendingInsecureUrl by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
            val html = descriptionHtml
            if (html.isNullOrBlank()) {
                Text(
                    stringResource(R.string.show_notes_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val sanitized = ShowNotesSanitizer.sanitize(html)
                val onSurface = MaterialTheme.colorScheme.onSurface.toArgb()
                AndroidView(
                    factory = { viewContext ->
                        TextView(viewContext).apply { movementMethod = LinkMovementMethod.getInstance() }
                    },
                    update = { view ->
                        view.setTextColor(onSurface)
                        val rendered = HtmlCompat.fromHtml(sanitized, HtmlCompat.FROM_HTML_MODE_COMPACT)
                        view.text = interceptLinks(rendered) { url ->
                            if (ShowNotesLinks.isSecure(url)) openLink(context, url) else pendingInsecureUrl = url
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    pendingInsecureUrl?.let { url ->
        InsecureLinkDialog(
            url = url,
            onConfirm = {
                openLink(context, url)
                pendingInsecureUrl = null
            },
            onDismiss = { pendingInsecureUrl = null }
        )
    }
}

@Composable
private fun InsecureLinkDialog(url: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.show_notes_link_confirm_title)) },
        text = { Text(stringResource(R.string.show_notes_link_confirm_message, url)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_open)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

/** Replace each URLSpan with a click that routes through [onLink] instead of opening directly. */
private fun interceptLinks(source: CharSequence, onLink: (String) -> Unit): CharSequence {
    val builder = SpannableStringBuilder(source)
    builder.getSpans(0, builder.length, URLSpan::class.java).forEach { span ->
        val start = builder.getSpanStart(span)
        val end = builder.getSpanEnd(span)
        val flags = builder.getSpanFlags(span)
        val url = span.url
        builder.removeSpan(span)
        builder.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) = onLink(url)
            },
            start,
            end,
            if (flags == 0) Spanned.SPAN_EXCLUSIVE_EXCLUSIVE else flags
        )
    }
    return builder
}

private fun openLink(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (ignore: ActivityNotFoundException) {
        // No browser to handle the link; nothing safe to do but drop it.
    }
}
