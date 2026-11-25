package com.vachan.app.ui.me

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vachan.app.BuildConfig
import com.vachan.app.core.model.Profile
import com.vachan.app.core.model.ProfileVisibility
import com.vachan.app.data.AuthRepository
import com.vachan.app.data.ProfileRepository
import com.vachan.app.ui.brand.Brand
import com.vachan.app.ui.components.Avatar
import com.vachan.app.ui.components.Eyebrow
import com.vachan.app.ui.components.Gutter
import com.vachan.app.ui.components.ListDivider
import com.vachan.app.ui.components.ListGroup
import com.vachan.app.ui.components.ListRow
import com.vachan.app.ui.components.MatchScoreRing
import com.vachan.app.ui.components.Notice
import com.vachan.app.ui.components.NoticeAction
import com.vachan.app.ui.components.NoticeTone
import com.vachan.app.ui.components.PaperCard
import com.vachan.app.ui.components.QuietButton
import com.vachan.app.ui.components.VerificationBadges
import com.vachan.app.ui.components.VerifiedSeal
import com.vachan.app.ui.root.Nav
import com.vachan.app.ui.theme.AanganColors
import com.vachan.app.ui.theme.DisplayFamily
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MeViewModel @Inject constructor(profiles: ProfileRepository, private val auth: AuthRepository) : ViewModel() {
    val me = profiles.myProfile
    val session = auth.session
    fun signOut() = viewModelScope.launch { runCatching { auth.signOut() } }
}

private data class Fix(val text: String, val action: (Nav) -> Unit)

private fun fixes(p: Profile): List<Fix> = buildList {
    if (p.photos.isEmpty()) add(Fix("Add a clear, recent photo") { it.editProfile() })
    if (!p.verification.isVerified) add(Fix("Verify with a selfie — it's free") { it.verification() })
    if (p.about.length < 80) add(Fix("Write a fuller bio — ${Brand.NAME} can help") { it.editProfile() })
    if (p.values.size < 12) add(Fix("Answer all Values Compass questions (${p.values.size}/12)") { it.editProfile() })
    if (p.interests.size < 3) add(Fix("Add at least 3 interests") { it.editProfile() })
}

@Composable
fun MeScreen(nav: Nav, modifier: Modifier = Modifier, vm: MeViewModel = hiltViewModel()) {
    val me by vm.me.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    val p = me ?: return
    val ext = AanganColors.extended

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(start = Gutter, end = Gutter, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Avatar(p, size = 104.dp)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${p.firstName} ${p.lastInitial.take(1)}., ${p.age}", fontFamily = DisplayFamily, style = MaterialTheme.typography.headlineLarge)
                if (p.verification.isVerified) { Spacer(Modifier.width(8.dp)); VerifiedSeal(22.dp) }
            }
            Text("${p.profession} · ${p.location.city}", style = MaterialTheme.typography.bodyLarge, color = ext.muted)
            Text(
                "Member ${p.memberCode}${session?.phoneMasked?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}",
                style = MaterialTheme.typography.labelMedium, color = ext.muted, textAlign = TextAlign.Center,
            )
        }

        if (p.visibility == ProfileVisibility.HIDDEN) {
            Notice(
                tone = NoticeTone.CAUTION, icon = Icons.Outlined.PauseCircle, title = "Your profile is paused",
                text = "You won't appear in anyone's introductions or search. Existing chats continue.",
                action = { NoticeAction("Manage", nav.privacy) },
            )
        }
        if (!BuildConfig.FIREBASE_ENABLED) {
            Notice(title = "Demo mode", text = "Everything runs on this phone with fictional members. Add a Firebase config to connect the real backend.")
        }

        val completeness = p.completeness()
        val todo = fixes(p)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Eyebrow("Profile strength", Modifier.padding(horizontal = 4.dp))
            PaperCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MatchScoreRing(completeness, size = 84.dp, caption = "complete")
                    Column(Modifier.weight(1f)) {
                        Text(if (completeness >= 90) "Looking great" else "A few touches to go", style = MaterialTheme.typography.titleLarge)
                        Text("Complete profiles get up to 3× more replies.", style = MaterialTheme.typography.bodyMedium, color = ext.muted)
                    }
                }
                if (todo.isNotEmpty()) Column {
                    todo.forEachIndexed { i, f ->
                        if (i > 0) androidx.compose.material3.HorizontalDivider(color = ext.hairline)
                        Row(
                            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = { f.action(nav) }).heightIn(min = 48.dp).padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(8.dp).background(ext.accent, CircleShape))
                            Spacer(Modifier.width(12.dp))
                            Text(f.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = ext.muted)
                        }
                    }
                }
            }
        }

        if (p.verification.count > 0) VerificationBadges(p.verification)

        ListGroup(title = "Your profile") {
            ListRow("Edit profile", subtitle = "Details, values, preferences, bio", icon = Icons.Outlined.Edit, onClick = nav.editProfile)
            ListDivider(inset = 66.dp)
            ListRow("Photos", subtitle = "${p.photos.size} of 6 · location data removed", icon = Icons.Outlined.PhotoCamera, onClick = nav.editProfile)
            ListDivider(inset = 66.dp)
            ListRow("Verification", subtitle = "Selfie, ID and work — always free", icon = Icons.Outlined.Verified, onClick = nav.verification)
        }
        ListGroup(title = "Help") {
            ListRow(Brand.ASSISTANT, subtitle = "Tips on preferences, bios and safety", icon = Icons.Outlined.AutoAwesome, onClick = nav.assistant)
            ListDivider(inset = 66.dp)
            ListRow("Family Mode", subtitle = "Let parents or siblings help, on your terms", icon = Icons.Outlined.FamilyRestroom, onClick = nav.family)
        }
        ListGroup(title = "Privacy and safety") {
            ListRow("Privacy Center", subtitle = "Visibility, photos, app lock, your data", icon = Icons.Outlined.Lock, onClick = nav.privacy)
            ListDivider(inset = 66.dp)
            ListRow("Safety Center", subtitle = "Meet safely, scam guide, reports", icon = Icons.Outlined.Shield, onClick = nav.safety)
            ListDivider(inset = 66.dp)
            ListRow("Privacy notice", subtitle = "What we collect and why, in plain words", icon = Icons.Outlined.Description, onClick = nav.privacyNotice)
        }
        ListGroup {
            ListRow("Sign out", icon = Icons.AutoMirrored.Outlined.Logout, destructive = true, onClick = { confirmSignOut = true })
        }
        Text(
            "Grievance Officer (IT Rules 2021): ${Brand.GRIEVANCE_EMAIL}. We acknowledge within 24 hours and resolve within 15 days.\n${Brand.NAME} ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall, color = ext.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            containerColor = ext.card,
            title = { Text("Sign out?", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    if (BuildConfig.FIREBASE_ENABLED) "We'll remove your settings, photos and cached data from this phone, so the next person to use it sees nothing of yours. Your profile and chats stay safe on your account."
                    else "Demo mode keeps everything on this phone, so signing out erases your demo profile, chats and photos. The next person starts fresh.",
                )
            },
            confirmButton = { QuietButton("Sign out", { confirmSignOut = false; vm.signOut() }, color = ext.danger) },
            dismissButton = { QuietButton("Cancel", { confirmSignOut = false }) },
        )
    }
}
