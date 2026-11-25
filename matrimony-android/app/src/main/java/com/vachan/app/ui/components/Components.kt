package com.vachan.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ReportGmailerrorred
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vachan.app.ui.theme.AanganColors
import com.vachan.app.ui.theme.EyebrowStyle

/** Standard horizontal gutter for every screen. */
val Gutter = 20.dp

// ------------------------------------------------------------------------------- typography

/** Small, tracked, uppercase label that sits above a section. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = AanganColors.extended.muted) {
    Text(text.uppercase(), modifier = modifier, style = EyebrowStyle, color = color)
}

/** Large editorial screen title with an optional eyebrow and supporting line. */
@Composable
fun ScreenTitle(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (eyebrow != null) Eyebrow(eyebrow)
            Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AanganColors.extended.muted)
            }
        }
        trailing?.invoke()
    }
}

// ---------------------------------------------------------------------------------- buttons

private val ButtonHeight = 54.dp

@Composable
private fun ButtonContent(text: String, icon: ImageVector?, loading: Boolean, color: Color) {
    if (loading) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = color)
    } else {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The main call to action: an ink pill. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.heightIn(min = ButtonHeight),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContentColor = AanganColors.extended.muted,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) { ButtonContent(text, icon, loading, MaterialTheme.colorScheme.onPrimary) }
}

/** Expressing intent towards a person (send interest, accept): the brand clay. */
@Composable
fun AccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    val ext = AanganColors.extended
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.heightIn(min = ButtonHeight),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = ext.accent,
            contentColor = ext.onAccent,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContentColor = ext.muted,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) { ButtonContent(text, icon, loading, ext.onAccent) }
}

/** Secondary action: hairline outline on paper. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.heightIn(min = ButtonHeight),
        shape = CircleShape,
        border = BorderStroke(1.dp, if (enabled) MaterialTheme.colorScheme.outline.copy(alpha = 0.55f) else AanganColors.extended.hairline),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = AanganColors.extended.card, contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = 22.dp),
    ) { ButtonContent(text, icon, loading, contentColor) }
}

/** Low-emphasis text action. */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface, enabled: Boolean = true) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled, shape = CircleShape) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) color else AanganColors.extended.muted)
    }
}

/** A round icon button with a paper fill and hairline, used for back/close/pass. */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    container: Color = AanganColors.extended.card,
    content: Color = MaterialTheme.colorScheme.onSurface,
    bordered: Boolean = true,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(size),
        shape = CircleShape,
        color = container,
        contentColor = content,
        border = if (bordered) BorderStroke(1.dp, AanganColors.extended.hairline) else null,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(size * 0.45f))
        }
    }
}

// --------------------------------------------------------------------------------- surfaces

/** A paper card with a hairline edge — the basic container on the ivory canvas. */
@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 20.dp,
    color: Color = AanganColors.extended.card,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val inner: @Composable () -> Unit = {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
    val border = BorderStroke(1.dp, AanganColors.extended.hairline)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = color, border = border) { inner() }
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = color, border = border) { inner() }
    }
}

/** A titled section: eyebrow label above a paper card. */
@Composable
fun Section(
    title: String? = null,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (title != null) {
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Eyebrow(title)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AanganColors.extended.muted)
            }
        }
        PaperCard(content = content)
    }
}

enum class NoticeTone { NEUTRAL, INFO, SUCCESS, CAUTION, DANGER }

/** Inline message with a tinted surface. Used for safety warnings, tips and confirmations. */
@Composable
fun Notice(
    text: String,
    modifier: Modifier = Modifier,
    tone: NoticeTone = NoticeTone.NEUTRAL,
    title: String? = null,
    icon: ImageVector? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    val ext = AanganColors.extended
    val scheme = MaterialTheme.colorScheme
    val (bg, accent) = when (tone) {
        NoticeTone.NEUTRAL -> scheme.surfaceContainerLow to scheme.onSurface
        NoticeTone.INFO -> ext.accentContainer to ext.onAccentContainer
        NoticeTone.SUCCESS -> ext.verifiedContainer to ext.verified
        NoticeTone.CAUTION -> ext.cautionContainer to ext.caution
        NoticeTone.DANGER -> ext.dangerContainer to ext.danger
    }
    val glyph = icon ?: when (tone) {
        NoticeTone.SUCCESS -> Icons.Outlined.TaskAlt
        NoticeTone.CAUTION -> Icons.Outlined.WarningAmber
        NoticeTone.DANGER -> Icons.Outlined.ReportGmailerrorred
        else -> Icons.Outlined.Info
    }
    Surface(modifier.fillMaxWidth(), color = bg, shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = if (action != null) 6.dp else 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(glyph, contentDescription = null, tint = accent, modifier = Modifier.padding(top = 1.dp).size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, color = if (tone == NoticeTone.NEUTRAL) scheme.onSurface else accent)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface.copy(alpha = 0.86f))
                if (action != null) Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), content = action)
            }
        }
    }
}

/** A compact text action for use inside a [Notice]. */
@Composable
fun NoticeAction(text: String, onClick: () -> Unit, color: Color = MaterialTheme.colorScheme.onSurface) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp), modifier = Modifier.heightIn(min = 40.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
    }
}

// ------------------------------------------------------------------------------------ lists

/** iOS-style inset group: a paper card whose rows are separated by hairlines. */
@Composable
fun ListGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) Eyebrow(title, Modifier.padding(horizontal = 4.dp))
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = AanganColors.extended.card,
            border = BorderStroke(1.dp, AanganColors.extended.hairline),
        ) { Column(Modifier.padding(vertical = 4.dp), content = content) }
        if (footer != null) {
            Text(footer, style = MaterialTheme.typography.bodySmall, color = AanganColors.extended.muted, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

@Composable
fun ListDivider(inset: Dp = 16.dp) {
    HorizontalDivider(Modifier.padding(start = inset), thickness = 1.dp, color = AanganColors.extended.hairline)
}

@Composable
private fun RowIcon(icon: ImageVector, tint: Color) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp)) }
}

/** A row in a [ListGroup]. Rows with [onClick] show a chevron unless [trailing] is given. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    destructive: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val titleColor = if (destructive) AanganColors.extended.danger else MaterialTheme.colorScheme.onSurface
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick, role = Role.Button) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) RowIcon(icon, if (destructive) AanganColors.extended.danger else iconTint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AanganColors.extended.muted)
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = AanganColors.extended.muted)
        }
    }
}

@Composable
fun AanganSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            uncheckedBorderColor = Color.Transparent,
            uncheckedThumbColor = AanganColors.extended.card,
        ),
    )
}

/** A whole-row toggle (the entire row is the touch target, announced as a switch). */
@Composable
fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) RowIcon(icon, MaterialTheme.colorScheme.onSurface)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AanganColors.extended.muted)
        }
        AanganSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
fun RadioRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AanganColors.extended.muted)
        }
        RadioButton(
            selected = selected, onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary, unselectedColor = MaterialTheme.colorScheme.outline),
        )
    }
}

// ------------------------------------------------------------------------------------ chips

enum class TagTone { NEUTRAL, ACCENT, VERIFIED, GOLD, ON_PHOTO }

/** A small, non-interactive pill. */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, tone: TagTone = TagTone.NEUTRAL) {
    val ext = AanganColors.extended
    val (bg, fg) = when (tone) {
        TagTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerLow to MaterialTheme.colorScheme.onSurface
        TagTone.ACCENT -> ext.accentContainer to ext.onAccentContainer
        TagTone.VERIFIED -> ext.verifiedContainer to ext.verified
        TagTone.GOLD -> ext.goldContainer to ext.gold
        TagTone.ON_PHOTO -> Color.Black.copy(alpha = 0.38f) to Color.White
    }
    Row(
        modifier.clip(CircleShape).background(bg).padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, color = fg, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagRow(items: List<String>, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { Tag(it) }
    }
}

/** A selectable chip: hairline when off, solid ink when on. */
@Composable
fun SelectChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) scheme.primary else AanganColors.extended.card, label = "chipBg")
    val fg by animateColorAsState(if (selected) scheme.onPrimary else scheme.onSurface, label = "chipFg")
    Row(
        modifier
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, if (selected) Color.Transparent else AanganColors.extended.hairline, CircleShape)
            .selectable(selected = selected, enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = 15.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = fg, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = fg, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** Pill-shaped segmented control (replaces tab rows). */
@Composable
fun SegmentedTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val isSel = i == selected
            val bg by animateColorAsState(if (isSel) AanganColors.extended.card else Color.Transparent, label = "segBg")
            Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(bg)
                    .selectable(selected = isSel, role = Role.Tab, onClick = { onSelect(i) })
                    .heightIn(min = 40.dp)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (isSel) MaterialTheme.colorScheme.onSurface else AanganColors.extended.muted,
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------- feedback

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(84.dp).clip(RoundedCornerShape(topStart = 42.dp, topEnd = 42.dp, bottomStart = 14.dp, bottomEnd = 14.dp))
                .background(AanganColors.extended.accentContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = AanganColors.extended.accent, modifier = Modifier.size(34.dp)) }
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = AanganColors.extended.muted, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            action()
        }
    }
}

/** Shimmering placeholder block shown while content loads. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, shape: RoundedCornerShape = RoundedCornerShape(12.dp)) {
    val t = rememberInfiniteTransition(label = "skeleton")
    val x by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "shimmer")
    val base = MaterialTheme.colorScheme.surfaceContainer
    val hi = MaterialTheme.colorScheme.surfaceContainerLowest
    Box(
        modifier.clip(shape).background(
            Brush.linearGradient(
                colors = listOf(base, hi, base),
                start = androidx.compose.ui.geometry.Offset(x * 1400f - 400f, 0f),
                end = androidx.compose.ui.geometry.Offset(x * 1400f, 400f),
            ),
        ),
    )
}

/** Thin segmented progress for multi-step flows. */
@Composable
fun StepProgress(current: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(total) { i ->
            Box(
                Modifier.weight(1f).height(3.dp).clip(CircleShape)
                    .background(if (i <= current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh),
            )
        }
    }
}

/** Icon + label + value, used for profile "vitals". */
@Composable
fun InfoRow(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, contentDescription = null, tint = AanganColors.extended.muted, modifier = Modifier.padding(top = 2.dp).size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = AanganColors.extended.muted)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

// ------------------------------------------------------------------------------------ inputs

/** Text field with a paper fill, hairline border and an ink focus ring. */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supporting: String? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 8,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions = androidx.compose.foundation.text.KeyboardActions.Default,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    prefix: String? = null,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(18.dp),
    enabled: Boolean = true,
) {
    val ext = AanganColors.extended
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it, color = ext.muted) } },
        supportingText = supporting?.let { { Text(it) } },
        isError = isError,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix?.let { { Text(it) } },
        shape = shape,
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedContainerColor = ext.card,
            unfocusedContainerColor = ext.card,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedBorderColor = MaterialTheme.colorScheme.onSurface,
            unfocusedBorderColor = ext.hairline,
            focusedLabelColor = MaterialTheme.colorScheme.onSurface,
            cursorColor = MaterialTheme.colorScheme.onSurface,
            focusedSupportingTextColor = ext.muted,
            unfocusedSupportingTextColor = ext.muted,
        ),
    )
}

/** Ink snackbars that float above the tab bar. */
@Composable
fun AppSnackbarHost(state: androidx.compose.material3.SnackbarHostState, modifier: Modifier = Modifier) {
    androidx.compose.material3.SnackbarHost(state, modifier.padding(horizontal = 12.dp)) { data ->
        androidx.compose.material3.Snackbar(
            data,
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            actionColor = AanganColors.extended.accentContainer,
        )
    }
}

/** Single-choice chip group. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> SelectChip(label(o), selected = o == selected, onClick = { onSelect(o) }) }
    }
}

/** Multi-choice chip group with an optional maximum. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> MultiChips(options: List<T>, selected: Set<T>, label: (T) -> String, onChange: (Set<T>) -> Unit, modifier: Modifier = Modifier, max: Int = Int.MAX_VALUE) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            val on = o in selected
            SelectChip(
                label(o), selected = on,
                enabled = on || selected.size < max,
                onClick = { onChange(if (on) selected - o else if (selected.size < max) selected + o else selected) },
            )
        }
    }
}

/** A label above a group of fields inside a card. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = modifier.padding(top = 4.dp))
}
