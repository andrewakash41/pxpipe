package com.vachan.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vachan.app.ui.brand.Brand
import com.vachan.app.ui.components.Gutter
import com.vachan.app.ui.components.PaperCard
import com.vachan.app.ui.components.ScreenTitle
import com.vachan.app.ui.components.TopBar
import com.vachan.app.ui.theme.AanganColors

private data class NoticeSection(val title: String, val points: List<String>)

/**
 * The privacy notice in plain language (DPDP Act 2023, s.5: notice in clear and plain language,
 * itemising the data and purposes, and how to exercise rights and complain).
 */
private val sections = listOf(
    NoticeSection(
        "What we collect",
        listOf(
            "Your mobile number, to sign you in. It is never shown on your profile.",
            "The profile you write: first name and last initial, age (from your date of birth, which we don't display), city, education, work, lifestyle, Values Compass answers, partner preferences and photos.",
            "Optional details you choose to add, such as religion, community or income. These are hidden unless you switch them on.",
            "What you do in the app — interests, shortlists, passes — to improve your introductions.",
            "A one-year log of the IP address used to create your profile, which the MeitY matrimonial advisory requires.",
        ),
    ),
    NoticeSection(
        "What we never collect",
        listOf(
            "Your precise location. We use your city's centre point only.",
            "Your Aadhaar number or a copy of your ID. Verification stores the result (\"name matches, age confirmed\") and nothing else.",
            "Your contacts, call logs or other apps' data.",
        ),
    ),
    NoticeSection(
        "Why we use it",
        listOf(
            "Running your account — matching, chat, safety and security. This is the only purpose you can't switch off, because the app can't work without it.",
            "AI matchmaking help — only if you opt in. The AI sees de-identified details (no name, number, photos or exact location) and never decides who you're shown.",
            "Anonymous product analytics and news — only if you opt in, and never for advertising.",
        ),
    ),
    NoticeSection(
        "Who can see what",
        listOf(
            "Other members see your public profile, according to your visibility and photo settings.",
            "Your phone number is shared with a match only when you choose, and you can take it back.",
            "Family members you invite see only what you allow, and never your chats.",
            "Our safety team sees reports and the messages a reporter chooses to attach.",
            "We never sell your data or use your photos for ads.",
        ),
    ),
    NoticeSection(
        "Where it lives and for how long",
        listOf(
            "Your data is stored in India (Mumbai). On your phone it is encrypted and excluded from backups.",
            "Screenshots and screen recording are blocked inside the app.",
            "If you delete your account, everything is erased after a 30-day undo window. The sign-up IP log is kept separately for one year, as the law requires.",
        ),
    ),
    NoticeSection(
        "Your rights",
        listOf(
            "See and download your data at any time from Privacy Center.",
            "Correct your profile, withdraw any optional consent, or delete your account in two taps.",
            "Nominate someone to exercise these rights if you're unable to, by writing to our Grievance Officer.",
            "Complain to our Grievance Officer at ${Brand.GRIEVANCE_EMAIL} — we acknowledge within 24 hours and resolve within 15 days — or to the Data Protection Board of India.",
        ),
    ),
)

@Composable
fun PrivacyNoticeContent(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        ScreenTitle("Privacy notice", eyebrow = "${Brand.NAME} · plain words", subtitle = "What we collect, why, and the control you have. Questions? ${Brand.PRIVACY_EMAIL}")
        sections.forEach { s ->
            PaperCard {
                Text(s.title, style = MaterialTheme.typography.headlineSmall)
                s.points.forEach { pt ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.padding(top = 9.dp).size(5.dp).background(AanganColors.extended.accent, CircleShape))
                        Text(pt, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun PrivacyNoticeScreen(onBack: () -> Unit, closeIcon: Boolean = false) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { if (closeIcon) TopBar(onBack = onBack, backIcon = Icons.Filled.Close, backDescription = "Close") else TopBar(onBack = onBack) },
    ) { padding ->
        PrivacyNoticeContent(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(start = Gutter, end = Gutter, bottom = 40.dp),
        )
    }
}
