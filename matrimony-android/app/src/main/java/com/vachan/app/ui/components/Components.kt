package com.vachan.app.ui.components

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vachan.app.core.model.Profile
import com.vachan.app.core.model.Verification
import com.vachan.app.ui.theme.VachanColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Profile photo with privacy built in:
 *  - [blurred] shows a heavily blurred preview (photo privacy "after I accept");
 *  - [watermark] overlays the *viewer's* member code and date, discouraging screenshots-by-camera
 *    and making leaked images traceable. (Screenshots themselves are blocked app-wide.)
 */
@Composable
fun ProfilePhoto(
    profile: Profile,
    modifier: Modifier = Modifier,
    size: Dp? = null,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    blurred: Boolean = false,
    watermark: String? = null,
) {
    val sized = if (size != null) modifier.size(size) else modifier
    Box(sized.clip(shape).semantics { contentDescription = "Photo of ${profile.firstName}" }) {
        val photo = profile.photos.firstOrNull()
        val blurMod = if (blurred && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(28.dp) else Modifier
        if (photo?.url != null) {
            AsyncImage(model = photo.url, contentDescription = null, modifier = Modifier.fillMaxSize().then(blurMod))
        } else {
            IllustratedAvatar(seed = photo?.seed ?: profile.id.hashCode(), initials = profile.firstName.take(1) + profile.lastInitial.take(1), modifier = Modifier.fillMaxSize().then(blurMod))
        }
        if (blurred) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.15f else 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White)
                    Text("Visible after they accept", color = Color.White, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                }
            }
        }
        if (watermark != null && !blurred) {
            WatermarkOverlay(watermark)
        }
    }
}

@Composable
private fun WatermarkOverlay(viewerCode: String) {
    val stamp = remember(viewerCode) { "$viewerCode · " + SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date()) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(36.dp), modifier = Modifier.rotate(-28f)) {
            repeat(4) {
                Text(stamp, color = Color.White.copy(alpha = 0.22f), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

/** Deterministic gradient avatar used for fictional Demo profiles (we never ship stock photos of real people). */
@Composable
fun IllustratedAvatar(seed: Int, initials: String, modifier: Modifier = Modifier) {
    val palettes = listOf(
        listOf(Color(0xFF9A1B3C), Color(0xFFE0951F)),
        listOf(Color(0xFF00696B), Color(0xFF9CF1F2)),
        listOf(Color(0xFF5B3A8E), Color(0xFFE6A5C8)),
        listOf(Color(0xFFB4541C), Color(0xFFFFD08A)),
        listOf(Color(0xFF2E5E2B), Color(0xFFB8E0A0)),
        listOf(Color(0xFF243B6B), Color(0xFF9DB8F2)),
    )
    val colors = palettes[Math.floorMod(seed, palettes.size)]
    Box(modifier.background(Brush.linearGradient(colors)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension
            drawCircle(Color.White.copy(alpha = 0.10f), radius = r * 0.55f, center = Offset(size.width * 0.85f, size.height * 0.1f))
            drawCircle(Color.White.copy(alpha = 0.08f), radius = r * 0.35f, center = Offset(size.width * 0.1f, size.height * 0.95f))
            // Simple head-and-shoulders silhouette.
            drawCircle(Color.White.copy(alpha = 0.28f), radius = r * 0.18f, center = Offset(size.width / 2, size.height * 0.40f))
            drawCircle(Color.White.copy(alpha = 0.22f), radius = r * 0.34f, center = Offset(size.width / 2, size.height * 0.98f))
        }
        Text(initials.uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun CompatibilityRing(score: Int, modifier: Modifier = Modifier, size: Dp = 56.dp, stroke: Dp = 6.dp) {
    val progress by animateFloatAsState(score / 100f, label = "compat")
    val track = MaterialTheme.colorScheme.surfaceVariant
    val color = when {
        score >= 80 -> MaterialTheme.colorScheme.tertiary
        score >= 60 -> VachanColors.extended.accent
        else -> MaterialTheme.colorScheme.outline
    }
    Box(modifier.size(size).semantics { contentDescription = "$score percent compatible" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val s = Stroke(width = w, cap = StrokeCap.Round)
            // Inset by half the stroke so the ring stays inside its bounds.
            val inset = Offset(w / 2, w / 2)
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w)
            drawArc(track, startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = inset, size = arcSize, style = s)
            drawArc(color, startAngle = -90f, sweepAngle = 360f * progress, useCenter = false, topLeft = inset, size = arcSize, style = s)
        }
        Text("$score", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VerificationBadges(v: Verification, modifier: Modifier = Modifier, compact: Boolean = false) {
    val items = buildList {
        if (v.selfie) add(Icons.Filled.Verified to "Selfie verified")
        if (v.governmentId) add(Icons.Filled.Badge to "ID verified")
        if (v.workEmail) add(Icons.Filled.Work to "Work verified")
        if (v.phone && !compact) add(Icons.Filled.PhoneAndroid to "Phone verified")
    }
    if (compact) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            items.forEach { (icon, label) ->
                Icon(icon, contentDescription = label, tint = VachanColors.extended.verified, modifier = Modifier.size(18.dp))
            }
        }
    } else {
        // Wraps onto a second line on narrow screens instead of overflowing.
        FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { (icon, label) ->
                Pill(text = label, icon = icon, container = VachanColors.extended.verified.copy(alpha = 0.12f), content = VachanColors.extended.verified)
            }
        }
    }
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Row(
        modifier.clip(CircleShape).background(container).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = content, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PillRow(items: List<String>, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { Pill(it, container = MaterialTheme.colorScheme.surfaceContainerHigh, content = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) {
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

enum class BannerTone { INFO, CAUTION, DANGER, SUCCESS }

@Composable
fun Banner(text: String, modifier: Modifier = Modifier, tone: BannerTone = BannerTone.INFO, title: String? = null, icon: ImageVector? = null, action: (@Composable () -> Unit)? = null) {
    val (bg, fg) = when (tone) {
        BannerTone.INFO -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        BannerTone.CAUTION -> VachanColors.extended.cautionContainer to VachanColors.extended.caution
        BannerTone.DANGER -> VachanColors.extended.dangerContainer to VachanColors.extended.danger
        BannerTone.SUCCESS -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }
    val resolvedIcon = icon ?: when (tone) {
        BannerTone.CAUTION, BannerTone.DANGER -> Icons.Filled.Warning
        else -> Icons.Outlined.Info
    }
    Surface(modifier.fillMaxWidth(), color = bg, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(resolvedIcon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, color = fg)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = if (tone == BannerTone.INFO) fg else MaterialTheme.colorScheme.onSurface)
                action?.invoke()
            }
        }
    }
}

@Composable
fun InfoRow(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(34.dp)) }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

@Composable
fun StepProgress(current: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(total) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(if (i <= current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

@Composable
fun FactorBar(label: String, detail: String, score: Double, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text("${(score * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(
                Modifier
                    .fillMaxWidth(score.toFloat().coerceIn(0.03f, 1f))
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, VachanColors.extended.accent))),
            )
        }
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun OutlinedBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium).padding(PaddingValues(14.dp)),
    ) { content() }
}
