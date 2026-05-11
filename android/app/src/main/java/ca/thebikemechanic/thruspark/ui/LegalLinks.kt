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

/**
 * Privacy + Terms hosted on the Bike Mechanic site. Update these constants
 * when the URLs change (e.g. when a versioned policy goes live).
 */
const val URL_PRIVACY_POLICY = "https://thebikemechanic.ca/privacy"
const val URL_TERMS_OF_SERVICE = "https://thebikemechanic.ca/terms"

/** End-to-end Shizuku setup walkthrough on YouTube. Update when video is re-cut. */
const val URL_SETUP_VIDEO = "https://youtu.be/cpKqXyKN9k0"

/**
 * GitHub releases page for the open-source build. Used by Settings → About →
 * "Verify this build" so users can match their installed APK against the
 * published SHA256.
 *
 * TODO: replace REPLACE_WITH_REAL_OWNER once the GitHub repo is published
 * (see project_thruspark_v05_open_source.md memory note for status).
 */
const val URL_GITHUB_RELEASES = "https://github.com/REPLACE_WITH_REAL_OWNER/ExpeditionMode/releases"

/**
 * Inline "By creating an account..." consent text shown on SignUpScreen.
 * Both [Terms] and [Privacy Policy] are tappable LinkAnnotations that open in
 * the system browser. Compose handles the click for us.
 */
@Composable
fun PrivacyConsentText(
    modifier: Modifier = Modifier
) {
    val linkStyle = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline
        )
    )
    val annotated = buildAnnotatedString {
        append("By creating an account, you agree to our ")
        withLink(LinkAnnotation.Url(URL_TERMS_OF_SERVICE, linkStyle)) {
            append("Terms")
        }
        append(" and ")
        withLink(LinkAnnotation.Url(URL_PRIVACY_POLICY, linkStyle)) {
            append("Privacy Policy")
        }
        append(".")
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

/**
 * Compact "Privacy · Terms" footer for screens that don't have a natural place
 * for the consent paragraph (Welcome screen, Settings).
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
            append("Privacy Policy")
        }
        append("  ·  ")
        withLink(LinkAnnotation.Url(URL_TERMS_OF_SERVICE, linkStyle)) {
            append("Terms of Service")
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
