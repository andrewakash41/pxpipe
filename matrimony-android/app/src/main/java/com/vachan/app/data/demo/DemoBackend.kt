package com.vachan.app.data.demo

import androidx.datastore.core.DataStore
import com.vachan.app.core.matching.CompatibilityEngine
import com.vachan.app.core.matching.CompatibilityResult
import com.vachan.app.core.matching.FactorWeights
import com.vachan.app.core.matching.PreferenceLearner
import com.vachan.app.core.matching.QueryParser
import com.vachan.app.core.matching.RankedMatch
import com.vachan.app.core.matching.Recommender
import com.vachan.app.core.model.ConsentPurpose
import com.vachan.app.core.model.ConsentRecord
import com.vachan.app.core.model.Conversation
import com.vachan.app.core.model.FamilyActivity
import com.vachan.app.core.model.FamilyManager
import com.vachan.app.core.model.FamilyPermission
import com.vachan.app.core.model.Gender
import com.vachan.app.core.model.Interest
import com.vachan.app.core.model.InterestStatus
import com.vachan.app.core.model.MatchInsight
import com.vachan.app.core.model.Message
import com.vachan.app.core.model.Photo
import com.vachan.app.core.model.Profile
import com.vachan.app.core.model.ProfileVisibility
import com.vachan.app.core.model.Report
import com.vachan.app.core.model.ReportReason
import com.vachan.app.core.model.SearchFilters
import com.vachan.app.core.model.minimumMarriageAge
import com.vachan.app.core.photos.PhotoStore
import com.vachan.app.core.safety.PhoneNumbers
import com.vachan.app.core.safety.PiiDetector
import com.vachan.app.core.security.SecureStore
import com.vachan.app.data.AssistantTurn
import com.vachan.app.data.Backend
import com.vachan.app.data.Session
import com.vachan.app.data.SettingsRepository
import com.vachan.app.ui.brand.Brand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.UUID

@Serializable
data class DemoState(
    val session: Session? = null,
    val myProfile: Profile? = null,
    val interests: List<Interest> = emptyList(),
    val conversations: List<Conversation> = emptyList(),
    val messages: List<Message> = emptyList(),
    val shortlist: Set<String> = emptySet(),
    val passed: Set<String> = emptySet(),
    val blocked: Set<String> = emptySet(),
    val reports: List<Report> = emptyList(),
    val managers: List<FamilyManager> = emptyList(),
    val familyActivity: List<FamilyActivity> = emptyList(),
    val scriptProgress: Map<String, Int> = emptyMap(),
    val seeded: Boolean = false,
    val pendingWorkEmail: String? = null,
    /** The member's own visibility choice, restored if they cancel a pending deletion. */
    val visibilityBeforeDeletion: ProfileVisibility? = null,
)

/**
 * A complete on-device stand-in for the backend, so the app can be built and explored without a
 * Firebase project. State is persisted with [SecureStore] (encrypted). Other members are fictional
 * and their replies are simulated.
 */
class DemoBackend(
    secureStore: SecureStore,
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val photoStore: PhotoStore,
) : Backend {

    private val store: DataStore<DemoState> = secureStore.dataStore("demo_state", DemoState.serializer(), DemoState(), scope)
    private val state = MutableStateFlow(runBlocking { store.data.first() })
    private val learner = PreferenceLearner()
    private val people: Map<String, Profile> = DemoSeed.all().associateBy { it.id }
    private val random = SecureRandom()
    @Volatile private var pendingPhone: String? = null

    init {
        // One ordered writer: every state change is persisted in sequence (intermediate states may
        // be skipped), so a slow write can never overwrite a newer state with an older one.
        scope.launch { state.drop(1).conflate().collect { s -> store.updateData { s } } }
    }

    /** Atomic read-modify-write; safe to call from any thread. */
    private fun mutate(transform: (DemoState) -> DemoState) = state.update(transform)

    /**
     * A read-only view of [state] that is always current: `.value` is computed from the latest
     * state, so a read straight after a write never sees stale data (unlike `stateIn` on a
     * background dispatcher, which lags by a dispatch).
     */
    private fun <T> derived(select: (DemoState) -> T): StateFlow<T> = DerivedStateFlow(state, select)

    private fun now() = System.currentTimeMillis()

    private fun engine(): CompatibilityEngine =
        CompatibilityEngine(FactorWeights.fromStored(settingsRepository.settings.value.learnedWeights))

    private fun isPersona(id: String) = id == DemoSeed.SCAM_PERSONA_MAN || id == DemoSeed.SCAM_PERSONA_WOMAN

    // ------------------------------------------------------------------------------------- Auth

    override val session: StateFlow<Session?> = derived { it.session }

    override suspend fun sendOtp(phoneE164: String, resend: Boolean): Result<Unit> {
        delay(600)
        if (!PhoneNumbers.e164.matches(phoneE164)) return Result.failure(IllegalArgumentException("Enter a valid mobile number"))
        pendingPhone = phoneE164
        return Result.success(Unit)
    }

    override suspend fun verifyOtp(code: String): Result<Session> {
        delay(500)
        val phone = pendingPhone ?: return Result.failure(IllegalStateException("Request a new code"))
        if (code != DEMO_OTP) return Result.failure(IllegalArgumentException("That code didn't match. In Demo mode the code is $DEMO_OTP."))
        val s = Session(userId = "me_" + phone.takeLast(4), phoneMasked = PhoneNumbers.mask(phone))
        pendingPhone = null
        mutate { it.copy(session = s) }
        return Result.success(s)
    }

    /** Shared-phone privacy: signing out leaves nothing of this member behind on the device. */
    override suspend fun signOut() {
        pendingPhone = null
        state.value = DemoState()
        store.updateData { DemoState() }
        photoStore.clear()
        settingsRepository.reset()
    }

    // ---------------------------------------------------------------------------------- Profile

    override val myProfile: StateFlow<Profile?> = derived { it.myProfile }

    override suspend fun saveMyProfile(profile: Profile): Result<Profile> {
        delay(300)
        if (profile.age < profile.gender.minimumMarriageAge()) {
            return Result.failure(IllegalArgumentException("You must be at least the legal marriageable age to join"))
        }
        // Server-side rules: contact details are stripped from bios; trust fields come from the server, not the client.
        val existing = state.value.myProfile
        val clean = profile.copy(
            id = state.value.session?.userId ?: profile.id,
            about = PiiDetector.redact(profile.about),
            verification = existing?.verification ?: profile.verification.copy(phone = true, selfie = false, governmentId = false, workEmail = false),
            memberCode = existing?.memberCode?.ifBlank { null } ?: "${Brand.MEMBER_CODE_PREFIX}-${100000 + random.nextInt(900000)}",
            // Only photos that actually exist in the encrypted store count.
            photos = profile.photos.filter { photoStore.read(it.id) != null }.map { it.copy(url = null) },
        )
        mutate { it.copy(myProfile = clean) }
        if (!state.value.seeded) seedWelcomeActivity(clean)
        return Result.success(clean)
    }

    override suspend fun getProfile(id: String): Profile? = if (id == state.value.myProfile?.id) state.value.myProfile else people[id]

    override suspend fun addPhoto(jpeg: ByteArray): Result<Photo> {
        val me = state.value.myProfile ?: return Result.failure(IllegalStateException("Create your profile first"))
        if (me.photos.size >= MAX_PHOTOS) return Result.failure(IllegalStateException("You can add up to $MAX_PHOTOS photos"))
        val photo = Photo(id = "ph_" + UUID.randomUUID().toString().replace("-", "").take(16), seed = random.nextInt())
        photoStore.write(photo.id, jpeg)
        mutate { s -> s.copy(myProfile = s.myProfile?.let { it.copy(photos = it.photos + photo) }) }
        return Result.success(photo)
    }

    override suspend fun removePhoto(id: String): Result<Unit> {
        mutate { s -> s.copy(myProfile = s.myProfile?.let { p -> p.copy(photos = p.photos.filterNot { it.id == id }) }) }
        photoStore.delete(id)
        return Result.success(Unit)
    }

    override suspend fun setPrimaryPhoto(id: String): Result<Unit> {
        mutate { s ->
            s.copy(myProfile = s.myProfile?.let { p ->
                val target = p.photos.firstOrNull { it.id == id } ?: return@let p
                p.copy(photos = listOf(target) + p.photos.filterNot { it.id == id })
            })
        }
        return Result.success(Unit)
    }

    private fun seedWelcomeActivity(me: Profile) {
        val opposite = people.values.filter { it.gender == me.seeking && !isPersona(it.id) }
        val e = engine()
        val ranked = opposite.filter { e.isEligible(me, it) }.sortedByDescending { e.score(me, it).score }
        val received = ranked.drop(3).take(2)
        val genuine = ranked.getOrNull(5) ?: ranked.firstOrNull()
        val scam = people[if (me.seeking == Gender.FEMALE) DemoSeed.SCAM_PERSONA_WOMAN else DemoSeed.SCAM_PERSONA_MAN]!!
        val t = now()
        mutate { s ->
            var next = s.copy(seeded = true)
            received.forEachIndexed { i, p ->
                next = next.copy(interests = next.interests + Interest(UUID.randomUUID().toString(), p.id, me.id, note = if (i == 0) "Loved reading your profile — would be happy to connect." else null, createdAt = t - (i + 1) * 3_600_000L))
            }
            listOfNotNull(genuine, scam).forEach { p ->
                val matchId = matchIdFor(me.id, p.id)
                val script = if (p.id == scam.id) DemoSeed.scamScript else DemoSeed.genuineScript
                next = next.copy(
                    conversations = next.conversations + Conversation(matchId, p.id, createdAt = t - 86_400_000L, lastMessage = script.first(), lastMessageAt = t - 600_000L, unread = 1),
                    messages = next.messages + Message(UUID.randomUUID().toString(), matchId, p.id, script.first(), t - 600_000L),
                    scriptProgress = next.scriptProgress + (matchId to 1),
                )
            }
            next
        }
    }

    // ---------------------------------------------------------------------------------- Matches

    override val shortlist: StateFlow<Set<String>> = derived { it.shortlist }

    override val learnedInsight: StateFlow<String?> = settingsRepository.settings
        .map { learner.explain(FactorWeights.fromStored(it.learnedWeights)) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private fun candidatePool(): List<Profile> = people.values.filter { !isPersona(it.id) }

    override suspend fun dailyPicks(): List<RankedMatch> {
        val me = state.value.myProfile ?: return emptyList()
        val s = state.value
        val interacted = s.interests.flatMap { listOf(it.fromId, it.toId) }.toSet() + s.conversations.map { it.otherId }
        return Recommender(engine()).dailyPicks(
            viewer = me,
            candidates = candidatePool(),
            excludeIds = interacted + s.passed,
            blockedIds = s.blocked,
            count = settingsRepository.settings.value.dailyPicksCount,
        )
    }

    override suspend fun search(filters: SearchFilters): List<RankedMatch> {
        val me = state.value.myProfile ?: return emptyList()
        val results = candidatePool().filter { QueryParser.matches(it, filters) }
        return Recommender(engine()).rankSearchResults(me, results, state.value.blocked)
    }

    override suspend fun compatibilityWith(otherId: String): CompatibilityResult? {
        val me = state.value.myProfile ?: return null
        val other = people[otherId] ?: return null
        return engine().score(me, other)
    }

    override suspend fun recordFeedback(otherId: String, feedback: PreferenceLearner.Feedback) {
        val c = compatibilityWith(otherId) ?: return
        settingsRepository.update { s ->
            val updated = learner.update(FactorWeights.fromStored(s.learnedWeights), c.factors, feedback)
            s.copy(learnedWeights = updated.toStored())
        }
    }

    override suspend fun toggleShortlist(otherId: String): Result<Unit> {
        val adding = otherId !in state.value.shortlist
        mutate { it.copy(shortlist = if (adding) it.shortlist + otherId else it.shortlist - otherId) }
        if (adding) recordFeedback(otherId, PreferenceLearner.Feedback.SHORTLISTED)
        return Result.success(Unit)
    }

    override suspend fun pass(otherId: String) {
        mutate { it.copy(passed = it.passed + otherId) }
        recordFeedback(otherId, PreferenceLearner.Feedback.PASSED)
    }

    override suspend fun resetLearning() {
        settingsRepository.update { it.copy(learnedWeights = emptyMap()) }
    }

    // -------------------------------------------------------------------------------- Interests

    override val interests: StateFlow<List<Interest>> = derived { s -> s.interests.map { expireIfDue(it) } }

    private fun expireIfDue(i: Interest) =
        if (i.status == InterestStatus.PENDING && now() > i.expiresAt) i.copy(status = InterestStatus.EXPIRED) else i

    override suspend fun sendInterest(toId: String, note: String?): Result<Interest> {
        val me = state.value.myProfile ?: return Result.failure(IllegalStateException("Create your profile first"))
        val todays = state.value.interests.count { it.fromId == me.id && now() - it.createdAt < 86_400_000L }
        if (todays >= FREE_DAILY_INTERESTS) return Result.failure(IllegalStateException("You've sent $FREE_DAILY_INTERESTS interests today. Take time with the ones you've sent — more tomorrow."))
        if (state.value.interests.any { it.fromId == me.id && it.toId == toId && it.status == InterestStatus.PENDING }) {
            return Result.failure(IllegalStateException("You've already sent an interest"))
        }
        val interest = Interest(UUID.randomUUID().toString(), me.id, toId, note = note?.let { PiiDetector.redact(it) }, createdAt = now())
        mutate { it.copy(interests = it.interests + interest) }
        recordFeedback(toId, PreferenceLearner.Feedback.INTEREST_SENT)
        simulateResponse(interest)
        return Result.success(interest)
    }

    /** Fictional members respond based on two-way compatibility, so the demo behaves plausibly. */
    private fun simulateResponse(interest: Interest) {
        scope.launch {
            delay(4_000)
            val c = compatibilityWith(interest.toId) ?: return@launch
            val other = people[interest.toId] ?: return@launch
            if (state.value.interests.firstOrNull { it.id == interest.id }?.status != InterestStatus.PENDING) return@launch
            when {
                c.score >= 66 -> {
                    updateInterest(interest.id) { it.copy(status = InterestStatus.ACCEPTED) }
                    val matchId = openConversation(other.id) ?: return@launch
                    delay(2_500)
                    receive(matchId, other.id, "Hi! Thanks for your interest 😊 I liked that we both care about ${c.highlights.firstOrNull()?.removePrefix("You agree on ")?.lowercase() ?: "similar things"}.")
                }
                c.score < 50 -> updateInterest(interest.id) {
                    it.copy(status = InterestStatus.DECLINED, declineMessage = DemoAi.kindDecline(state.value.myProfile?.firstName ?: "there"))
                }
                else -> Unit // Stays pending; will expire in 7 days like a real unanswered interest.
            }
        }
    }

    private fun updateInterest(id: String, transform: (Interest) -> Interest) =
        mutate { s -> s.copy(interests = s.interests.map { if (it.id == id) transform(it) else it }) }

    override suspend fun accept(interestId: String): Result<String> {
        val interest = state.value.interests.firstOrNull { it.id == interestId } ?: return Result.failure(NoSuchElementException("This interest no longer exists"))
        if (expireIfDue(interest).status != InterestStatus.PENDING) return Result.failure(IllegalStateException("This interest was already answered or has expired"))
        updateInterest(interestId) { it.copy(status = InterestStatus.ACCEPTED) }
        val matchId = openConversation(interest.fromId) ?: return Result.failure(IllegalStateException("Create your profile first"))
        scope.launch {
            delay(3_000)
            receive(matchId, interest.fromId, "So happy you accepted! How's your week going?")
        }
        return Result.success(matchId)
    }

    override suspend fun decline(interestId: String, message: String?): Result<Unit> {
        updateInterest(interestId) { it.copy(status = InterestStatus.DECLINED, declineMessage = message?.let { m -> PiiDetector.redact(m) }) }
        state.value.interests.firstOrNull { it.id == interestId }?.let { recordFeedback(it.fromId, PreferenceLearner.Feedback.PASSED) }
        return Result.success(Unit)
    }

    override suspend fun withdraw(interestId: String): Result<Unit> {
        updateInterest(interestId) { it.copy(status = InterestStatus.WITHDRAWN) }
        return Result.success(Unit)
    }

    // ------------------------------------------------------------------------------------- Chat

    override val conversations: StateFlow<List<Conversation>> =
        derived { s -> s.conversations.filter { it.otherId !in s.blocked }.sortedByDescending { it.lastMessageAt ?: it.createdAt } }

    override fun messages(matchId: String): Flow<List<Message>> =
        state.map { s -> s.messages.filter { it.matchId == matchId }.sortedBy { it.sentAt } }

    private fun matchIdFor(a: String, b: String) = listOf(a, b).sorted().joinToString("_")

    private fun openConversation(otherId: String): String? {
        val me = state.value.myProfile ?: return null
        val id = matchIdFor(me.id, otherId)
        mutate { s ->
            if (s.conversations.any { it.matchId == id }) s
            else s.copy(conversations = s.conversations + Conversation(id, otherId, createdAt = now()))
        }
        return id
    }

    private fun receive(matchId: String, from: String, text: String) {
        val t = now()
        mutate { s ->
            if (from in s.blocked) return@mutate s
            s.copy(
                messages = s.messages + Message(UUID.randomUUID().toString(), matchId, from, text, t),
                conversations = s.conversations.map {
                    if (it.matchId == matchId) it.copy(lastMessage = text, lastMessageAt = t, unread = it.unread + 1) else it
                },
            )
        }
    }

    override suspend fun sendMessage(matchId: String, text: String): Result<Unit> {
        val me = state.value.myProfile ?: return Result.failure(IllegalStateException("Create your profile first"))
        val convo = state.value.conversations.firstOrNull { it.matchId == matchId } ?: return Result.failure(NoSuchElementException("This conversation is no longer available"))
        if (convo.closed) return Result.failure(IllegalStateException("This conversation is closed"))
        if (text.isBlank() || text.length > 2000) return Result.failure(IllegalArgumentException("Messages can be up to 2,000 characters"))
        val t = now()
        mutate { s ->
            s.copy(
                messages = s.messages + Message(UUID.randomUUID().toString(), matchId, me.id, text.trim(), t),
                conversations = s.conversations.map { if (it.matchId == matchId) it.copy(lastMessage = text.trim(), lastMessageAt = t) else it },
            )
        }
        scope.launch { simulateReply(convo) }
        return Result.success(Unit)
    }

    private suspend fun simulateReply(convo: Conversation) {
        delay(3_500)
        if (convo.otherId in state.value.blocked) return
        val script = if (isPersona(convo.otherId)) DemoSeed.scamScript else DemoSeed.genuineScript
        val progress = state.value.scriptProgress[convo.matchId] ?: 0
        val reply = script.getOrNull(progress) ?: listOf(
            "That's lovely to hear! Would you be open to a video call this weekend?",
            "Haha, I agree. My family would love that story too.",
            "Maybe we can meet for coffee somewhere central once we've talked a bit more?",
        ).random()
        mutate { it.copy(scriptProgress = it.scriptProgress + (convo.matchId to progress + 1)) }
        receive(convo.matchId, convo.otherId, reply)
    }

    override suspend fun setContactShared(matchId: String, shared: Boolean): Result<Unit> {
        val convo = state.value.conversations.firstOrNull { it.matchId == matchId } ?: return Result.failure(NoSuchElementException("This conversation is no longer available"))
        if (convo.closed) return Result.failure(IllegalStateException("This conversation is closed"))
        val notice = if (shared) "You shared your phone number. You can take it back any time." else "You stopped sharing your phone number."
        mutate { s ->
            s.copy(
                conversations = s.conversations.map { if (it.matchId == matchId) it.copy(iSharedContact = shared) else it },
                messages = s.messages + Message(UUID.randomUUID().toString(), matchId, "system", notice, now(), system = true),
            )
        }
        if (shared && !isPersona(convo.otherId)) {
            scope.launch {
                delay(2_000)
                mutate { s -> s.copy(conversations = s.conversations.map { if (it.matchId == matchId) it.copy(theySharedContact = true) else it }) }
            }
        }
        return Result.success(Unit)
    }

    override suspend fun markRead(matchId: String) {
        mutate { s -> s.copy(conversations = s.conversations.map { if (it.matchId == matchId) it.copy(unread = 0) else it }) }
    }

    override suspend fun sharedContact(matchId: String): String? {
        val c = state.value.conversations.firstOrNull { it.matchId == matchId } ?: return null
        if (c.closed || !c.theySharedContact) return null
        return "+91 9" + c.otherId.hashCode().toString().takeLast(4).padStart(4, '0') + " 01234"
    }

    override suspend fun closeConversation(matchId: String): Result<Unit> {
        mutate { s -> s.copy(conversations = s.conversations.map { if (it.matchId == matchId) it.copy(closed = true, iSharedContact = false) else it }) }
        return Result.success(Unit)
    }

    // --------------------------------------------------------------------------------------- AI

    override val aiEnabled: StateFlow<Boolean> = settingsRepository.settings
        .map { it.hasConsent(ConsentPurpose.AI_ASSISTANCE) }
        .stateIn(scope, SharingStarted.Eagerly, false)

    override suspend fun matchInsight(other: Profile, compatibility: CompatibilityResult): MatchInsight {
        delay(400)
        return DemoAi.insight(state.value.myProfile ?: other, other, compatibility)
    }

    override suspend fun icebreakers(other: Profile, compatibility: CompatibilityResult): List<String> =
        DemoAi.icebreakers(state.value.myProfile ?: other, other, compatibility)

    override suspend fun draftBio(profile: Profile, tone: String, language: String): String {
        delay(500)
        return DemoAi.bio(profile, tone)
    }

    override suspend fun parseSearch(query: String): SearchFilters = DemoAi.parseSearch(query)

    override suspend fun kindDecline(otherFirstName: String): String = DemoAi.kindDecline(otherFirstName)

    override suspend fun translate(text: String, targetLanguage: String): String = DemoAi.translate(text, targetLanguage)

    override suspend fun ask(history: List<AssistantTurn>): String {
        delay(500)
        return DemoAi.assistant(history, state.value.myProfile, learnedInsight.value)
    }

    // ----------------------------------------------------------------------------------- Safety

    override val blocked: StateFlow<Set<String>> = derived { it.blocked }
    override val reports: StateFlow<List<Report>> = derived { it.reports }

    override suspend fun report(userId: String, reason: ReportReason, details: String, attachRecentMessages: Boolean): Result<Unit> {
        val r = Report(UUID.randomUUID().toString(), userId, reason, details.take(1000), now())
        mutate { it.copy(reports = it.reports + r) }
        return Result.success(Unit)
    }

    override suspend fun block(userId: String): Result<Unit> {
        mutate { s ->
            s.copy(
                blocked = s.blocked + userId,
                conversations = s.conversations.map { if (it.otherId == userId) it.copy(closed = true, iSharedContact = false, theySharedContact = false) else it },
                interests = s.interests.map { if ((it.fromId == userId || it.toId == userId) && it.status == InterestStatus.PENDING) it.copy(status = InterestStatus.WITHDRAWN) else it },
                shortlist = s.shortlist - userId,
            )
        }
        return Result.success(Unit)
    }

    override suspend fun unblock(userId: String): Result<Unit> {
        mutate { it.copy(blocked = it.blocked - userId) }
        return Result.success(Unit)
    }

    // ---------------------------------------------------------------------------------- Privacy

    override suspend fun setConsent(purpose: ConsentPurpose, granted: Boolean) {
        if (purpose.required) return
        settingsRepository.update { it.copy(consents = it.consents + (purpose to ConsentRecord(granted, now()))) }
    }

    override suspend fun exportMyData(): Result<String> {
        val s = state.value
        val export = DataExport(
            exportedAt = now(),
            profile = s.myProfile,
            interests = s.interests,
            conversations = s.conversations,
            messagesSentByMe = s.messages.filter { it.senderId == s.myProfile?.id },
            shortlist = s.shortlist,
            blocked = s.blocked,
            reports = s.reports,
            familyManagers = s.managers.map { it.copy(inviteCode = "") },
            settings = settingsRepository.settings.value,
        )
        return Result.success(exportJson.encodeToString(DataExport.serializer(), export))
    }

    override suspend fun requestDeletion(): Result<Unit> {
        settingsRepository.update { it.copy(deletionRequestedAt = now()) }
        mutate { s ->
            val current = s.myProfile?.visibility
            s.copy(
                visibilityBeforeDeletion = if (current != null && current != ProfileVisibility.HIDDEN) current else s.visibilityBeforeDeletion ?: current,
                myProfile = s.myProfile?.copy(visibility = ProfileVisibility.HIDDEN),
            )
        }
        return Result.success(Unit)
    }

    override suspend fun cancelDeletion(): Result<Unit> {
        settingsRepository.update { it.copy(deletionRequestedAt = null) }
        mutate { s ->
            s.copy(
                myProfile = s.myProfile?.copy(visibility = s.visibilityBeforeDeletion ?: ProfileVisibility.EVERYONE),
                visibilityBeforeDeletion = null,
            )
        }
        return Result.success(Unit)
    }

    // ----------------------------------------------------------------------------------- Family

    override val managers: StateFlow<List<FamilyManager>> = derived { it.managers }
    override val activity: StateFlow<List<FamilyActivity>> = derived { it.familyActivity }

    override suspend fun invite(name: String, relation: String, permissions: Set<FamilyPermission>): Result<FamilyManager> {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val code = (1..8).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        val m = FamilyManager(UUID.randomUUID().toString(), name.trim().take(40), relation, permissions, inviteCode = code)
        mutate { it.copy(managers = it.managers + m, familyActivity = it.familyActivity + FamilyActivity(m.name, "was invited", now())) }
        scope.launch {
            delay(5_000)
            mutate { s ->
                if (s.managers.none { it.id == m.id }) return@mutate s
                s.copy(
                    managers = s.managers.map { if (it.id == m.id) it.copy(accepted = true, inviteCode = "") else it },
                    familyActivity = s.familyActivity + FamilyActivity(m.name, "accepted your invitation", now()),
                )
            }
        }
        return Result.success(m)
    }

    override suspend fun updatePermissions(managerId: String, permissions: Set<FamilyPermission>): Result<Unit> {
        mutate { s -> s.copy(managers = s.managers.map { if (it.id == managerId) it.copy(permissions = permissions) else it }) }
        return Result.success(Unit)
    }

    override suspend fun remove(managerId: String): Result<Unit> {
        mutate { s ->
            val m = s.managers.firstOrNull { it.id == managerId }
            s.copy(
                managers = s.managers.filterNot { it.id == managerId },
                familyActivity = if (m != null) s.familyActivity + FamilyActivity(m.name, "was removed", now()) else s.familyActivity,
            )
        }
        return Result.success(Unit)
    }

    // ----------------------------------------------------------------------------- Verification

    private fun updateVerification(transform: (com.vachan.app.core.model.Verification) -> com.vachan.app.core.model.Verification) =
        mutate { s -> s.copy(myProfile = s.myProfile?.let { it.copy(verification = transform(it.verification)) }) }

    override suspend fun submitSelfie(challengePassed: Boolean): Result<Unit> {
        delay(1_500)
        if (!challengePassed) return Result.failure(IllegalStateException("We couldn't confirm the gestures. Try again in good light."))
        updateVerification { it.copy(selfie = true) }
        return Result.success(Unit)
    }

    override suspend fun startGovernmentId(): Result<String> {
        delay(1_500)
        updateVerification { it.copy(governmentId = true) }
        return Result.success("Demo: DigiLocker consent simulated. We stored only \"name matches, age confirmed\" — never your Aadhaar number.")
    }

    override suspend fun sendWorkEmailCode(email: String): Result<Unit> {
        if (!Regex("""[^@\s]+@[^@\s]+\.[a-z]{2,}""", RegexOption.IGNORE_CASE).matches(email)) return Result.failure(IllegalArgumentException("Enter a valid work email"))
        val domain = email.substringAfter('@').lowercase()
        if (domain in setOf("gmail.com", "yahoo.com", "yahoo.co.in", "outlook.com", "hotmail.com", "rediffmail.com", "icloud.com", "proton.me", "protonmail.com")) {
            return Result.failure(IllegalArgumentException("Please use your work email, not a personal one"))
        }
        mutate { it.copy(pendingWorkEmail = email) }
        return Result.success(Unit)
    }

    override suspend fun confirmWorkEmail(code: String): Result<Unit> {
        if (state.value.pendingWorkEmail == null) return Result.failure(IllegalStateException("Request a code first"))
        if (code != DEMO_OTP) return Result.failure(IllegalArgumentException("Wrong code. In Demo mode it's $DEMO_OTP."))
        updateVerification { it.copy(workEmail = true) }
        mutate { it.copy(pendingWorkEmail = null) }
        return Result.success(Unit)
    }

    companion object {
        const val DEMO_OTP = "123456"
        const val FREE_DAILY_INTERESTS = 5
        const val MAX_PHOTOS = 6
        private val exportJson = Json { prettyPrint = true; encodeDefaults = true; allowStructuredMapKeys = true }
    }
}

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private class DerivedStateFlow<S, T>(private val source: StateFlow<S>, private val select: (S) -> T) : StateFlow<T> {
    override val value: T get() = select(source.value)
    override val replayCache: List<T> get() = listOf(value)
    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        source.map(select).distinctUntilChanged().collect(collector)
        awaitCancellation()
    }
}

@Serializable
data class DataExport(
    val exportedAt: Long,
    val profile: Profile?,
    val interests: List<Interest>,
    val conversations: List<Conversation>,
    val messagesSentByMe: List<Message>,
    val shortlist: Set<String>,
    val blocked: Set<String>,
    val reports: List<Report>,
    val familyManagers: List<FamilyManager>,
    val settings: com.vachan.app.core.model.AppSettings,
)
