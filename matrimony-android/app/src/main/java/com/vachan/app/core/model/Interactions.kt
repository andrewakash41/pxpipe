package com.vachan.app.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class InterestStatus { PENDING, ACCEPTED, DECLINED, EXPIRED, WITHDRAWN }

@Serializable
data class Interest(
    val id: String,
    val fromId: String,
    val toId: String,
    val note: String? = null,
    val status: InterestStatus = InterestStatus.PENDING,
    val createdAt: Long,
    /** Interests expire after 7 days so nobody is left waiting forever. */
    val expiresAt: Long = createdAt + INTEREST_TTL_MS,
    val declineMessage: String? = null,
) {
    companion object {
        const val INTEREST_TTL_MS: Long = 7L * 24 * 60 * 60 * 1000
    }
}

@Serializable
data class Message(
    val id: String,
    val matchId: String,
    val senderId: String,
    val text: String,
    val sentAt: Long,
    /** Set when the message is a system notice (contact shared, meeting planned, etc.). */
    val system: Boolean = false,
)

@Serializable
data class Conversation(
    val matchId: String,
    val otherId: String,
    val createdAt: Long,
    val lastMessage: String? = null,
    val lastMessageAt: Long? = null,
    val unread: Int = 0,
    /** Phone number shared by me / by them (each side consents separately and can revoke). */
    val iSharedContact: Boolean = false,
    val theySharedContact: Boolean = false,
    val closed: Boolean = false,
)

@Serializable
enum class ConsentPurpose(val title: String, val description: String, val required: Boolean) {
    ESSENTIAL(
        "Running your account",
        "Your profile, matches, chats, safety and security checks. Needed for the app to work.",
        required = true,
    ),
    AI_ASSISTANCE(
        "AI matchmaking assistance",
        "Match explanations, icebreakers, bio help, smart search and translation. The AI never sees your name, " +
            "number, photos or exact location. Matching itself works without this.",
        required = false,
    ),
    ANALYTICS(
        "Product analytics",
        "Anonymous usage statistics to improve the app. Never sold, never used for ads.",
        required = false,
    ),
    MARKETING(
        "Offers and news",
        "Occasional emails or notifications about new features. We never make sales calls.",
        required = false,
    ),
}

@Serializable
data class ConsentRecord(val granted: Boolean, val updatedAt: Long)

@Serializable
enum class AutoLockTimeout(val label: String, val millis: Long) {
    IMMEDIATELY("Immediately", 0),
    ONE_MINUTE("After 1 minute", 60_000),
    FIVE_MINUTES("After 5 minutes", 300_000),
    THIRTY_MINUTES("After 30 minutes", 1_800_000),
}

@Serializable
data class AppSettings(
    val onboarded: Boolean = false,
    val language: String = "English",
    val consents: Map<ConsentPurpose, ConsentRecord> = emptyMap(),
    val declaredIntentToMarry: Boolean = false,
    val appLockEnabled: Boolean = false,
    val autoLock: AutoLockTimeout = AutoLockTimeout.ONE_MINUTE,
    val privateNotifications: Boolean = true,
    val incognito: Boolean = false,
    val verifiedOnlyContact: Boolean = false,
    val requirePhotoToContact: Boolean = false,
    /** Visibility to restore when the member un-pauses their profile; null when not paused. */
    val visibilityBeforePause: ProfileVisibility? = null,
    val deletionRequestedAt: Long? = null,
    val dailyPicksCount: Int = 8,
    val trustedContactName: String = "",
    val trustedContactPhone: String = "",
    /** Deadline of an active "Meet safely" check-in (epoch ms), or null. Survives app restarts. */
    val checkInAt: Long? = null,
    /** Learned factor-weight multipliers (see PreferenceLearner). Empty = defaults. */
    val learnedWeights: Map<String, Double> = emptyMap(),
) {
    fun hasConsent(purpose: ConsentPurpose): Boolean =
        purpose.required || consents[purpose]?.granted == true
}

@Serializable
enum class ReportReason(val label: String) {
    SCAM("Asked for money or seems like a scam"),
    FAKE_PROFILE("Fake profile or stolen photos"),
    HARASSMENT("Harassment or abusive messages"),
    INAPPROPRIATE("Sexual or inappropriate content"),
    ALREADY_MARRIED("Already married"),
    UNDERAGE("May be under the legal age"),
    OFF_PLATFORM_PRESSURE("Pressuring to move to WhatsApp or another app"),
    OTHER("Something else"),
}

@Serializable
data class Report(
    val id: String,
    val reportedId: String,
    val reason: ReportReason,
    val details: String,
    val createdAt: Long,
    /** IT Rules 2021: acknowledge within 24 hours, resolve within 15 days. */
    val status: String = "Received — we'll acknowledge within 24 hours",
)

@Serializable
enum class FamilyPermission(val label: String) {
    VIEW_MATCHES("View matches"),
    SHORTLIST("Shortlist profiles"),
    SUGGEST("Suggest profiles to me"),
    EDIT_PROFILE("Edit my profile details"),
}

@Serializable
data class FamilyManager(
    val id: String,
    val name: String,
    val relation: String,
    val permissions: Set<FamilyPermission>,
    /** Present until the invite is accepted; the server deletes it on acceptance. */
    val inviteCode: String = "",
    val accepted: Boolean = false,
)

@Serializable
data class FamilyActivity(val managerName: String, val action: String, val at: Long)

@Serializable
data class SearchFilters(
    val minAge: Int? = null,
    val maxAge: Int? = null,
    val cities: Set<String> = emptySet(),
    val religions: Set<Religion> = emptySet(),
    val motherTongues: Set<String> = emptySet(),
    val diets: Set<Diet> = emptySet(),
    val professions: Set<String> = emptySet(),
    val minEducation: EducationLevel? = null,
    val maritalStatuses: Set<MaritalStatus> = emptySet(),
    val openToRelocate: Boolean? = null,
    val livesAbroad: Boolean? = null,
    val verifiedOnly: Boolean = false,
    val minHeightCm: Int? = null,
    val keywords: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = this == SearchFilters()

    /** Human-readable chips describing the filters (shown so users can see and edit what the AI understood). */
    fun describe(): List<String> = buildList {
        if (minAge != null || maxAge != null) add("Age ${minAge ?: 18}–${maxAge ?: 70}")
        cities.forEach { add(it) }
        religions.forEach { add(it.label) }
        motherTongues.forEach { add(it) }
        diets.forEach { add(it.label) }
        professions.forEach { add(it.replaceFirstChar(Char::uppercase)) }
        minEducation?.let { add("${it.label}+") }
        maritalStatuses.forEach { add(it.label) }
        if (openToRelocate == true) add("Open to relocating")
        if (livesAbroad == true) add("Lives abroad")
        if (livesAbroad == false) add("Lives in India")
        if (verifiedOnly) add("Verified only")
        minHeightCm?.let { add("Taller than ${heightLabel(it)}") }
        keywords.forEach { add("“$it”") }
    }
}

/** AI-written (or template) insight for a pair of profiles. */
@Serializable
data class MatchInsight(
    val summary: String,
    val talkingPoints: List<String>,
    val generatedByAi: Boolean,
)
