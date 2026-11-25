package com.vachan.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vachan.app.ui.theme.AanganColors

/**
 * Minimal top bar: a round back button, an optional centred title and trailing actions.
 * Sits on the ivory canvas with no fill, like an editorial page header.
 */
@Composable
fun TopBar(
    title: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    backIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    backDescription: String = "Back",
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .heightIn(min = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (title != null) {
            Text(
                title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 104.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) CircleIconButton(backIcon, backDescription, onBack)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

data class NavItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector, val badge: Int = 0)

/**
 * Floating pill tab bar. The selected tab expands into an ink capsule with its label; the others
 * are icon-only, which keeps five destinations calm. Each tab is a proper selectable with a label
 * for TalkBack, and badges are announced.
 */
@Composable
fun PillNavBar(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = CircleShape,
            color = AanganColors.extended.card,
            border = BorderStroke(1.dp, AanganColors.extended.hairline),
            modifier = Modifier.shadow(18.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.14f)),
        ) {
            Row(Modifier.padding(6.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                items.forEachIndexed { i, item -> PillNavTab(item, i == selected) { onSelect(i) } }
            }
        }
    }
}

@Composable
private fun PillNavTab(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) scheme.primary else Color.Transparent, label = "tabBg")
    val fg by animateColorAsState(if (selected) scheme.onPrimary else AanganColors.extended.muted, label = "tabFg")
    val a11y = item.label + if (item.badge > 0) ", ${item.badge} new" else ""
    Row(
        Modifier
            .clip(CircleShape)
            .background(bg)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = a11y }
            .height(48.dp)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
            if (item.badge > 0) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-3).dp).size(9.dp).clip(CircleShape)
                        .background(AanganColors.extended.accent),
                )
            }
        }
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + expandHorizontally(spring(stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut() + shrinkHorizontally(spring(stiffness = Spring.StiffnessMediumLow)),
        ) {
            Row {
                Spacer(Modifier.width(8.dp))
                Text(item.label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
            }
        }
    }
}
