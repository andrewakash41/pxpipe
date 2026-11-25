package com.vachan.app.ui.brand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vachan.app.ui.theme.AanganColors

/**
 * Every user-facing brand string lives here, so renaming the product is a one-file change
 * (plus `app_name` in the values and values-hi string resources). Code packages keep the original `com.vachan`
 * namespace so Firebase/App Check registrations are unaffected.
 */
object Brand {
    const val NAME = "Aangan"
    const val NAME_NATIVE = "आँगन"
    const val TAGLINE = "Where families begin."
    const val PROMISE = "Verified people. Serious intent. Your data stays yours."
    const val ASSISTANT = "Aangan Guide"
    const val GRIEVANCE_EMAIL = "grievance@aangan.example"
    const val PRIVACY_EMAIL = "privacy@aangan.example"
    const val MEMBER_CODE_PREFIX = "AGN"
}

/**
 * The mark: a home seen from above — a rounded square — with an arched doorway opening into the
 * courtyard. Drawn in code so it stays crisp at every size and follows light/dark themes.
 */
@Composable
fun AanganMark(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    frame: Color = MaterialTheme.colorScheme.onBackground,
    door: Color = AanganColors.extended.accent,
) {
    Canvas(modifier.size(size).semantics { contentDescription = "${Brand.NAME} logo" }) {
        val s = this.size.minDimension
        val stroke = s * 0.095f
        val inset = stroke / 2
        drawRoundRect(
            color = frame,
            topLeft = Offset(inset, inset),
            size = Size(s - stroke, s - stroke),
            cornerRadius = CornerRadius(s * 0.3f, s * 0.3f),
            style = Stroke(width = stroke),
        )
        val doorW = s * 0.34f
        val left = (s - doorW) / 2
        val bottom = s
        val shoulder = s * 0.52f
        val arch = Path().apply {
            moveTo(left, bottom)
            lineTo(left, shoulder)
            arcTo(Rect(left, shoulder - doorW / 2, left + doorW, shoulder + doorW / 2), 180f, 180f, false)
            lineTo(left + doorW, bottom)
            close()
        }
        drawPath(arch, door)
    }
}

/** Mark + lowercase serif wordmark, used on the welcome and lock screens. */
@Composable
fun AanganWordmark(
    modifier: Modifier = Modifier,
    markSize: Dp = 30.dp,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AanganMark(size = markSize, frame = color)
        Text(Brand.NAME.lowercase(), style = style, color = color)
    }
}
