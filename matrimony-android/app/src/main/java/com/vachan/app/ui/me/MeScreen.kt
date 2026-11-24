package com.vachan.app.ui.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vachan.app.BuildConfig
import com.vachan.app.data.AuthRepository
import com.vachan.app.data.ProfileRepository
import com.vachan.app.ui.components.Banner
import com.vachan.app.ui.components.BannerTone
import com.vachan.app.ui.components.ProfilePhoto
import com.vachan.app.ui.components.SectionCard
import com.vachan.app.ui.components.SettingsRow
import com.vachan.app.ui.components.VerificationBadges
import com.vachan.app.ui.root.Nav
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MeViewModel @Inject constructor(profiles: ProfileRepository, private val auth: AuthRepository) : ViewModel() {
    val me = profiles.myProfile
    val session = auth.session
    fun signOut() = viewModelScope.launch { auth.signOut() }
}

@Composable
fun MeScreen(nav: Nav, modifier: Modifier = Modifier, vm: MeViewModel = hiltViewModel()) {
    val me by vm.me.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val p = me ?: return
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfilePhoto(p, size = 84.dp, shape = RoundedCornerShape(42.dp))
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text("${p.displayName}, ${p.age}", style = MaterialTheme.typography.headlineSmall)
                Text("${p.profession} · ${p.location.city}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Member code ${p.memberCode} · ${session?.phoneMasked ?: ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        VerificationBadges(p.verification)
        if (!BuildConfig.FIREBASE_ENABLED) {
            Banner(tone = BannerTone.INFO, title = "Demo mode", text = "Everything runs on this phone with fictional members. Add a Firebase config to connect the real backend.")
        }
        val completeness = p.completeness()
        SectionCard(title = "Profile strength · $completeness%", subtitle = "Complete profiles get up to 3× more responses") {
            LinearProgressIndicator(progress = { completeness / 100f }, modifier = Modifier.fillMaxWidth())
            if (!p.verification.isVerified) Text("• Verify with a selfie (free)", style = MaterialTheme.typography.bodyMedium)
            if (p.about.length < 80) Text("• Write a fuller bio — Vachan can help", style = MaterialTheme.typography.bodyMedium)
            if (p.values.size < 12) Text("• Answer all Values Compass questions (${p.values.size}/12)", style = MaterialTheme.typography.bodyMedium)
            if (p.interests.size < 3) Text("• Add at least 3 interests", style = MaterialTheme.typography.bodyMedium)
        }
        SectionCard {
            SettingsRow(Icons.Filled.Edit, "Edit profile", "Details, Values Compass, preferences, bio", onClick = nav.editProfile)
            SettingsRow(Icons.Filled.Verified, "Verification", "Selfie, government ID, work email — all free", onClick = nav.verification)
            SettingsRow(Icons.Filled.AutoAwesome, "Ask Vachan", "Your AI matchmaking assistant", onClick = nav.assistant)
            SettingsRow(Icons.Filled.FamilyRestroom, "Family Mode", "Let parents or siblings help, on your terms", onClick = nav.family)
            SettingsRow(Icons.Filled.Lock, "Privacy Center", "Visibility, photos, app lock, your data", onClick = nav.privacy)
            SettingsRow(Icons.Filled.Shield, "Safety Center", "Meet safely, reports, scam guide", onClick = nav.safety)
            SettingsRow(Icons.AutoMirrored.Filled.Logout, "Sign out", onClick = { vm.signOut() })
        }
        Text(
            "Grievance Officer (IT Rules 2021): grievance@vachan.example · we acknowledge within 24 hours and resolve within 15 days.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
