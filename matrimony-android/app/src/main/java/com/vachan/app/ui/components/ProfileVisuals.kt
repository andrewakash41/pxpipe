package com.vachan.app.ui.components

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
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
import com.vachan.app.core.photos.LocalPhotoStore
import com.vachan.app.ui.theme.AanganColors
import com.vachan.app.ui.theme.DisplayFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Real blur needs RenderEffect (Android 12+). Below that we never draw the photo at all. */
private val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * A member photo with privacy built in.
 *  - [blurred]: the member shares photos only after accepting an interest. On Android 12+ a heavy
 *    blur is drawn; on older versions the photo is **not rendered at all** (a dimmed overlay would
 *    still leak the face), and the illustrated avatar is shown instead.
 *  - [watermark]: the *viewer's* member code tiled faintly across the photo, so a photo taken of
 *    the screen with another phone is traceable. Screenshots themselves are blocked app-wide.
 *  - The member's own photos are read from the encrypted on-device store; everyone else's come
 *    from short-lived signed URLs and are cached in memory only.
 */
@Composable
fun ProfilePhoto(
    profile: Profile,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    blurred: Boolean = false,
    watermark: String? = null,
    index: Int = 0,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
) {
    val photo = profile.photos.getOrNull(index) ?: profile.photos.firstOrNull()
    val store = LocalPhotoStore.current
    val local by produceState<ByteArray?>(null, photo?.id, store) { value = photo?.id?.let { store?.read(it) } }
    val model: Any? = local ?: photo?.url
    val hideImage = blurred && !canBlur

    Box(modifier.clip(shape).semantics { contentDescription = "Photo of ${profile.firstName}" }) {
        val blurMod = if (blurred && canBlur) Modifier.blur(36.dp) else Modifier
        if (model != null && !hideImage) {
            AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().then(blurMod))
        } else {
            IllustratedAvatar(
                seed = photo?.seed ?: profile.id.hashCode(),
                initials = profile.firstName.take(1) + profile.lastInitial.take(1),
                modifier = Modifier.fillMaxSize().then(blurMod),
            )
        }
        if (blurred) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Text("Photos shared after they accept", color = Color.White, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                }
            }
        } else if (watermark != null) {
            Watermark(watermark)
        }
        overlay?.invoke(this)
    }
}

@Composable
private fun Watermark(viewerCode: String) {
    val stamp = remember(viewerCode) { "$viewerCode  ·  " + SimpleDateFormat("d MMM", Locale.getDefault()).format(Date()) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(72.dp), modifier = Modifier.rotate(-24f)) {
            repeat(5) { row ->
                Text(
                    if (row % 2 == 0) "$stamp      $stamp" else "      $stamp      $stamp",
                    color = Color.White.copy(alpha = 0.085f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
                )
            }
        }
    }
}

private val avatarPalettes = listOf(
    Color(0xFFB0532F) to Color(0xFFEBC0A0), // clay
    Color(0xFF2F6A50) to Color(0xFFB3D4BF), // sage
    Color(0xFF2B3A55) to Color(0xFFA9B8D3), // indigo
    Color(0xFF5B3A55) to Color(0xFFD9B2CB), // plum
    Color(0xFF8A6420) to Color(0xFFEBD199), // ochre
    Color(0xFF8E3B46) to Color(0xFFE9B1AE), // rose
)

/**
 * Placeholder portrait for members without a photo (and for the fictional Demo members — we never
 * ship photos of real people). A warm gradient with an arched "window" and serif initials.
 */
@Composable
fun IllustratedAvatar(seed: Int, initials: String, modifier: Modifier = Modifier) {
    val (deep, light) = avatarPalettes[Math.floorMod(seed, avatarPalettes.size)]
    Box(modifier.background(Brush.linearGradient(listOf(deep, light), start = Offset.Zero, end = Offset.Infinite)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val archW = minOf(w, h) * 0.62f
            val left = (w - archW) / 2
            val top = h * 0.2f
            val arch = Path().apply {
                moveTo(left, h)
                lineTo(left, top + archW / 2)
                arcTo(Rect(left, top, left + archW, top + archW), 180f, 180f, false)
                lineTo(left + archW, h)
                close()
            }
            drawPath(arch, Color.White.copy(alpha = 0.16f))
            drawCircle(Color.White.copy(alpha = 0.10f), radius = minOf(w, h) * 0.5f, center = Offset(w * 0.92f, h * 0.04f))
        }
        val big = initials.uppercase()
        Text(
            big,
            color = Color.White,
            fontFamily = DisplayFamily,
            fontSize = 44.sp,
            modifier = Modifier.padding(top = 24.dp),
        )
    }
}

/** Small circular avatar for lists and chat headers. */
@Composable
fun Avatar(profile: Profile, size: Dp = 48.dp, blurred: Boolean = false, modifier: Modifier = Modifier) {
    val photo = profile.photos.firstOrNull()
    val store = LocalPhotoStore.current
    val local by produceState<ByteArray?>(null, photo?.id, store) { value = photo?.id?.let { store?.read(it) } }
    val model: Any? = local ?: photo?.url
    Box(modifier.size(size).clip(CircleShape).semantics { contentDescription = profile.firstName }, contentAlignment = Alignment.Center) {
        if (model != null && !blurred) {
            AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            val (deep, light) = avatarPalettes[Math.floorMod(photo?.seed ?: profile.id.hashCode(), avatarPalettes.size)]
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(deep, light))), contentAlignment = Alignment.Center) {
                Text(
                    (profile.firstName.take(1) + profile.lastInitial.take(1)).uppercase(),
                    color = Color.White, fontFamily = DisplayFamily, fontSize = (size.value * 0.42f).sp,
                )
            }
        }
    }
}

/** Sage verification seal shown next to names. */
@Composable
fun VerifiedSeal(size: Dp = 18.dp, modifier: Modifier = Modifier, onPhoto: Boolean = false) {
    Icon(
        Icons.Filled.Verified, contentDescription = "Verified",
        tint = if (onPhoto) Color.White else AanganColors.extended.verified, modifier = modifier.size(size),
    )
}

/** Two-way compatibility as a thin ring with a serif numeral. */
@Composable
fun MatchScoreRing(score: Int, modifier: Modifier = Modifier, size: Dp = 64.dp, stroke: Dp = 4.dp, caption: String? = "match") {
    val progress by animateFloatAsState(score / 100f, animationSpec = tween(900), label = "score")
    val track = AanganColors.extended.hairline
    val color = if (score >= 85) AanganColors.extended.gold else AanganColors.extended.accent
    Box(modifier.size(size).semantics(mergeDescendants = true) { contentDescription = "$score percent two-way match" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val inset = Offset(w / 2, w / 2)
            val arc = Size(this.size.width - w, this.size.height - w)
            drawArc(track, -90f, 360f, false, topLeft = inset, size = arc, style = Stroke(w))
            drawArc(color, -90f, 360f * progress, false, topLeft = inset, size = arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", fontFamily = DisplayFamily, fontSize = (size.value * 0.36f).sp, color = MaterialTheme.colorScheme.onSurface)
            if (caption != null && size >= 60.dp) Text(caption, style = MaterialTheme.typography.labelSmall, color = AanganColors.extended.muted)
        }
    }
}

/** "92% match" capsule that sits on top of a photo. */
@Composable
fun ScorePill(score: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.36f)).padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = if (score >= 85) Color(0xFFF1D79B) else Color.White, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text("$score% match", color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VerificationBadges(v: Verification, modifier: Modifier = Modifier) {
    val items = buildList {
        if (v.selfie) add(Icons.Filled.Verified to "Selfie verified")
        if (v.governmentId) add(Icons.Filled.Badge to "ID verified")
        if (v.workEmail) add(Icons.Filled.Work to "Work verified")
        if (v.phone) add(Icons.Filled.PhoneAndroid to "Phone verified")
    }
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (icon, label) -> Tag(label, icon = icon, tone = TagTone.VERIFIED) }
    }
}

/** A quiet horizontal bar for one compatibility factor. */
@Composable
fun FactorBar(label: String, detail: String, score: Double, modifier: Modifier = Modifier) {
    val pct = (score * 100).toInt().coerceIn(0, 100)
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("$pct%", style = MaterialTheme.typography.labelMedium, color = AanganColors.extended.muted)
        }
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(AanganColors.extended.hairline)) {
            Box(
                Modifier.fillMaxWidth(score.toFloat().coerceIn(0.02f, 1f)).height(4.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface),
            )
        }
        Text(detail, style = MaterialTheme.typography.bodySmall, color = AanganColors.extended.muted)
    }
}
