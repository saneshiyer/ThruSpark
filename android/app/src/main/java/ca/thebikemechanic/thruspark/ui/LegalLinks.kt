package ca.thebikemechanic.thruspark.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

/** Privacy + Terms anchors live in the GitHub repo README. */
const val URL_PRIVACY_POLICY = "https://github.com/saneshiyer/ThruSpark#privacy"
const val URL_TERMS_OF_SERVICE = "https://github.com/saneshiyer/ThruSpark#terms"

/** End-to-end Shizuku setup walkthrough on YouTube. */
const val URL_SETUP_VIDEO = "https://youtu.be/cpKqXyKN9k0"

/**
 * GitHub releases page for the open-source build. Used by Settings → About →
 * "Verify this build" so users can match their installed APK against the
 * published SHA256.
 */
const val URL_GITHUB_RELEASES = "https://github.com/saneshiyer/ThruSpark/releases"

/**
 * Compact "Privacy · Terms" footer for screens that don't have a natural place
 * for a consent paragraph.
 */
@Composable
fun LegalFooterLinks(
    modifier: Modifier = Modifier
) {
    val linkStyle = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = TextDecoration.Underline
        )
    )
    val annotated = buildAnnotatedString {
        withLink(LinkAnnotation.Url(URL_PRIVACY_POLICY, linkStyle)) {
            append("Privacy")
        }
        append("  ·  ")
        withLink(LinkAnnotation.Url(URL_TERMS_OF_SERVICE, linkStyle)) {
            append("Terms")
        }
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}
