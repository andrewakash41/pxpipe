package com.vachan.app.data

import com.vachan.app.core.matching.CompatibilityResult
import com.vachan.app.core.matching.PreferenceLearner
import com.vachan.app.core.matching.RankedMatch
import com.vachan.app.core.model.AppSettings
import com.vachan.app.core.model.ConsentPurpose
import com.vachan.app.core.model.Conversation
import com.vachan.app.core.model.FamilyActivity
import com.vachan.app.core.model.FamilyManager
import com.vachan.app.core.model.FamilyPermission
import com.vachan.app.core.model.Interest
import com.vachan.app.core.model.MatchInsight
import com.vachan.app.core.model.Message
import com.vachan.app.core.model.Photo
import com.vachan.app.core.model.Profile
import com.vachan.app.core.model.Report
import com.vachan.app.core.model.ReportReason
import com.vachan.app.core.model.SearchFilters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Repository contracts. Two implementations exist:
 *  - `demo/DemoBackend`: on-device fake backend with seeded profiles (no network), used when the
 *    app is built without a Firebase config;
 *  - `firebase/FirebaseBackend`: the real backend (Auth, Firestore, Cloud Functions in asia-south1).
 *
 * Anything that can fail over the network returns [Result] so a dropped connection becomes a
 * message on screen, never an uncaught exception in a ViewModel coroutine (which crashes the app).
 */

@Serializable
data class Session(val userId: String, val phoneMasked: String)

interface AuthRepository {
    val session: StateFlow<Session?>
    /**
     * Starts phone verification for an E.164 number. [resend] re-sends to the same number.
     * In Demo mode any valid mobile number works and the code is 123456.
     */
    suspend fun sendOtp(phoneE164: String, resend: Boolean = false): Result<Unit>
    suspend fun verifyOtp(code: String): Result<Session>
    /** Signs out and wipes everything this member left on the device (shared-phone privacy). */
    suspend fun signOut()
}

interface ProfileRepository {
    val myProfile: StateFlow<Profile?>
    /** Server validates, moderates and publishes the public card; trust fields can't be set by clients. */
    suspend fun saveMyProfile(profile: Profile): Result<Profile>
    suspend fun getProfile(id: String): Profile?
    /** Adds an already metadata-stripped JPEG (see PhotoProcessor) to the member's own profile. */
    suspend fun addPhoto(jpeg: ByteArray): Result<Photo>
    suspend fun removePhoto(id: String): Result<Unit>
    suspend fun setPrimaryPhoto(id: String): Result<Unit>
}

interface MatchRepository {
    val shortlist: StateFlow<Set<String>>
    val learnedInsight: StateFlow<String?>
    suspend fun dailyPicks(): List<RankedMatch>
    suspend fun search(filters: SearchFilters): List<RankedMatch>
    suspend fun compatibilityWith(otherId: String): CompatibilityResult?
    suspend fun recordFeedback(otherId: String, feedback: PreferenceLearner.Feedback)
    suspend fun toggleShortlist(otherId: String): Result<Unit>
    suspend fun pass(otherId: String)
    suspend fun resetLearning()
}

interface InterestRepository {
    val interests: StateFlow<List<Interest>>
    suspend fun sendInterest(toId: String, note: String?): Result<Interest>
    suspend fun accept(interestId: String): Result<String>
    suspend fun decline(interestId: String, message: String?): Result<Unit>
    suspend fun withdraw(interestId: String): Result<Unit>
}

interface ChatRepository {
    val conversations: StateFlow<List<Conversation>>
    fun messages(matchId: String): Flow<List<Message>>
    suspend fun sendMessage(matchId: String, text: String): Result<Unit>
    suspend fun setContactShared(matchId: String, shared: Boolean): Result<Unit>
    suspend fun markRead(matchId: String)
    /** The other person's number, only when they chose to share it. */
    suspend fun sharedContact(matchId: String): String?
    suspend fun closeConversation(matchId: String): Result<Unit>
}

data class AssistantTurn(val fromUser: Boolean, val text: String)

interface AiRepository {
    /** Whether AI features are available (consent given and backend configured). */
    val aiEnabled: StateFlow<Boolean>
    suspend fun matchInsight(other: Profile, compatibility: CompatibilityResult): MatchInsight
    suspend fun icebreakers(other: Profile, compatibility: CompatibilityResult): List<String>
    suspend fun draftBio(profile: Profile, tone: String, language: String): String
    suspend fun parseSearch(query: String): SearchFilters
    suspend fun kindDecline(otherFirstName: String): String
    suspend fun translate(text: String, targetLanguage: String): String
    suspend fun ask(history: List<AssistantTurn>): String
}

interface SafetyRepository {
    val blocked: StateFlow<Set<String>>
    val reports: StateFlow<List<Report>>
    suspend fun report(userId: String, reason: ReportReason, details: String, attachRecentMessages: Boolean): Result<Unit>
    suspend fun block(userId: String): Result<Unit>
    suspend fun unblock(userId: String): Result<Unit>
}

interface SettingsRepository {
    val settings: StateFlow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
    /** Back to factory defaults (used on sign-out so nothing carries over to the next person). */
    suspend fun reset()
}

interface PrivacyRepository {
    suspend fun setConsent(purpose: ConsentPurpose, granted: Boolean)
    /** JSON export of everything we hold about the user (DPDP right to access). */
    suspend fun exportMyData(): Result<String>
    suspend fun requestDeletion(): Result<Unit>
    suspend fun cancelDeletion(): Result<Unit>
}

interface FamilyRepository {
    val managers: StateFlow<List<FamilyManager>>
    val activity: StateFlow<List<FamilyActivity>>
    suspend fun invite(name: String, relation: String, permissions: Set<FamilyPermission>): Result<FamilyManager>
    suspend fun updatePermissions(managerId: String, permissions: Set<FamilyPermission>): Result<Unit>
    suspend fun remove(managerId: String): Result<Unit>
}

enum class VerificationStep { SELFIE, GOVERNMENT_ID, WORK_EMAIL }

interface VerificationRepository {
    /** Called after the on-device liveness challenge; the vendor face-match runs server-side. */
    suspend fun submitSelfie(challengePassed: Boolean): Result<Unit>
    /** Starts DigiLocker / Aadhaar-offline consent flow; returns a message or redirect URL. */
    suspend fun startGovernmentId(): Result<String>
    suspend fun sendWorkEmailCode(email: String): Result<Unit>
    suspend fun confirmWorkEmail(code: String): Result<Unit>
}

/** Everything a backend provides; DI picks [demo.DemoBackend] or [firebase.FirebaseBackend]. */
interface Backend : AuthRepository, ProfileRepository, MatchRepository, InterestRepository, ChatRepository,
    AiRepository, SafetyRepository, PrivacyRepository, FamilyRepository, VerificationRepository
