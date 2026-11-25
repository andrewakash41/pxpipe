package com.vachan.app.data.firebase

import android.app.Activity
import android.content.Context
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.vachan.app.BuildConfig
import com.vachan.app.core.matching.CompatibilityEngine
import com.vachan.app.core.matching.CompatibilityResult
import com.vachan.app.core.matching.FactorWeights
import com.vachan.app.core.matching.PreferenceLearner
import com.vachan.app.core.matching.RankedMatch
import com.vachan.app.core.model.ConsentPurpose
import com.vachan.app.core.model.ConsentRecord
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
import com.vachan.app.core.photos.PhotoStore
import com.vachan.app.core.safety.PhoneNumbers
import com.vachan.app.core.security.DeviceIntegrity
import com.vachan.app.data.AssistantTurn
import com.vachan.app.data.Backend
import com.vachan.app.data.Session
import com.vachan.app.data.SettingsRepository
import com.vachan.app.data.demo.DemoAi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Phone auth needs an Activity for Play Integrity / reCAPTCHA fallback. */
object ActivityHolder {
    @Volatile private var ref: WeakReference<Activity>? = null
    fun set(activity: Activity) { ref = WeakReference(activity) }
    fun get(): Activity? = ref?.get()
}

/**
 * Real backend: Firebase Auth (phone OTP), Firestore and callable Cloud Functions, all in
 * `asia-south1` (Mumbai). Clients never write trust-bearing data directly: profiles, interests,
 * contact sharing, verification and reports go through Cloud Functions that validate, rate-limit
 * and audit. See `firebase/firestore.rules` for the access model.
 */
class FirebaseBackend(
    context: Context,
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val photoStore: PhotoStore,
) : Backend {

    private val appContext = context.applicationContext
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance().apply {
        // Memory-only cache: other people's profiles and chats are never written to disk.
        firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
            .build()
    }
    private val functions = FirebaseFunctions.getInstance(BuildConfig.FUNCTIONS_REGION)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false; allowStructuredMapKeys = true }
    private val learner = PreferenceLearner()
    /** Other members' cards, in memory only (never written to disk). */
    private val profileCache = mutableMapOf<String, Profile>()

    private val uid: String? get() = auth.currentUser?.uid

    // --------------------------------------------------------------------------- serialisation

    private fun toJsonString(any: Any?): String = when (any) {
        is Map<*, *> -> JSONObject(any).toString()
        is List<*> -> JSONArray(any).toString()
        is String -> JSONObject.quote(any)
        null -> "null"
        else -> any.toString()
    }

    private inline fun <reified T> decode(any: Any?): T = json.decodeFromString(toJsonString(any))
    private inline fun <reified T> encode(value: T): JSONObject = JSONObject(json.encodeToString(value))

    private suspend fun call(name: String, data: Map<String, Any?> = emptyMap()): Map<*, *> {
        val result = functions.getHttpsCallable(name).call(JSONObject(data)).await()
        return result.getData() as? Map<*, *> ?: emptyMap<String, Any?>()
    }

    private suspend fun <T> runCatchingCall(block: suspend () -> T): Result<T> =
        try { Result.success(block()) } catch (e: Exception) { Result.failure(friendly(e)) }

    /**
     * Turns SDK exceptions into messages a member can act on. Callable errors already carry the
     * server's user-facing message; everything else is mapped so no internals leak into the UI.
     */
    private fun friendly(e: Exception): Exception = when (e) {
        is FirebaseFunctionsException -> if (e.code == FirebaseFunctionsException.Code.INTERNAL || e.code == FirebaseFunctionsException.Code.UNKNOWN) {
            IllegalStateException("Something went wrong on our side. Please try again.")
        } else e
        is FirebaseNetworkException -> IllegalStateException("You're offline. Check your connection and try again.")
        is FirebaseTooManyRequestsException -> IllegalStateException("Too many attempts. Please wait a little and try again.")
        is FirebaseAuthInvalidCredentialsException -> IllegalArgumentException("That code didn't match. Check the SMS and try again.")
        is IllegalArgumentException, is IllegalStateException -> e
        else -> IllegalStateException("Something went wrong. Please try again.")
    }

    // ------------------------------------------------------------------------------------- Auth

    private val _session = MutableStateFlow(auth.currentUser?.let { Session(it.uid, mask(it.phoneNumber)) })
    override val session: StateFlow<Session?> = _session.asStateFlow()
    @Volatile private var verificationId: String? = null
    @Volatile private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    private fun mask(phone: String?): String = PhoneNumbers.mask(phone)

    override suspend fun sendOtp(phoneE164: String, resend: Boolean): Result<Unit> {
        val activity = ActivityHolder.get() ?: return Result.failure(IllegalStateException("Please try again"))
        if (!PhoneNumbers.e164.matches(phoneE164)) return Result.failure(IllegalArgumentException("Enter a valid mobile number"))
        return suspendCancellableCoroutine { cont ->
            val builder = PhoneAuthOptions.newBuilder(auth)
            val token = resendToken
            if (resend && token != null) builder.setForceResendingToken(token)
            val options = builder
                .setPhoneNumber(phoneE164)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                        // Instant verification / auto-retrieval.
                        auth.signInWithCredential(credential)
                        if (cont.isActive) cont.resume(Result.success(Unit))
                    }

                    override fun onVerificationFailed(e: FirebaseException) {
                        if (cont.isActive) cont.resume(Result.failure(friendly(e)))
                    }

                    override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                        verificationId = id
                        resendToken = token
                        if (cont.isActive) cont.resume(Result.success(Unit))
                    }
                })
                .build()
            PhoneAuthProvider.verifyPhoneNumber(options)
        }
    }

    override suspend fun verifyOtp(code: String): Result<Session> = runCatchingCall {
        val id = verificationId ?: error("Request a new code")
        val user = auth.signInWithCredential(PhoneAuthProvider.getCredential(id, code)).await().user ?: error("Sign-in failed")
        Session(user.uid, mask(user.phoneNumber))
    }

    /** Shared-phone privacy: sign out and wipe caches, own photos and local settings. */
    override suspend fun signOut() {
        auth.signOut()
        verificationId = null
        resendToken = null
        synchronized(profileCache) { profileCache.clear() }
        photoStore.clear()
        settingsRepository.reset()
    }

    // ------------------------------------------------------------------------- live listeners

    private val _myProfile = MutableStateFlow<Profile?>(null)
    override val myProfile: StateFlow<Profile?> = _myProfile.asStateFlow()
    private val _interests = MutableStateFlow<List<Interest>>(emptyList())
    override val interests: StateFlow<List<Interest>> = _interests.asStateFlow()
    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    override val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()
    private val _blocked = MutableStateFlow<Set<String>>(emptySet())
    override val blocked: StateFlow<Set<String>> = _blocked.asStateFlow()
    private val _reports = MutableStateFlow<List<Report>>(emptyList())
    override val reports: StateFlow<List<Report>> = _reports.asStateFlow()
    private val _shortlist = MutableStateFlow<Set<String>>(emptySet())
    override val shortlist: StateFlow<Set<String>> = _shortlist.asStateFlow()
    private val _managers = MutableStateFlow<List<FamilyManager>>(emptyList())
    override val managers: StateFlow<List<FamilyManager>> = _managers.asStateFlow()
    private val _activity = MutableStateFlow<List<FamilyActivity>>(emptyList())
    override val activity: StateFlow<List<FamilyActivity>> = _activity.asStateFlow()

    private val registrations = mutableListOf<ListenerRegistration>()
    private val sentInterests = MutableStateFlow<List<Interest>>(emptyList())
    private val receivedInterests = MutableStateFlow<List<Interest>>(emptyList())

    init {
        scope.launch {
            combine(sentInterests, receivedInterests) { a, b -> (a + b).sortedByDescending { it.createdAt } }
                .collect { _interests.value = it }
        }
    }

    private fun restartListeners() {
        registrations.forEach { it.remove() }
        registrations.clear()
        val me = uid ?: run {
            _myProfile.value = null; _interests.value = emptyList(); _conversations.value = emptyList()
            return
        }
        val userDoc = db.collection("users").document(me)
        registrations += userDoc.addSnapshotListener { snap, _ ->
            _myProfile.value = snap?.get("profile")?.let { runCatching { decode<Profile>(it) }.getOrNull() }
        }
        registrations += db.collection("interests").whereEqualTo("fromId", me).addSnapshotListener { snap, _ ->
            sentInterests.value = snap?.documents.orEmpty().mapNotNull { d -> runCatching { decode<Interest>(d.data!! + ("id" to d.id)) }.getOrNull() }
        }
        registrations += db.collection("interests").whereEqualTo("toId", me).addSnapshotListener { snap, _ ->
            receivedInterests.value = snap?.documents.orEmpty().mapNotNull { d -> runCatching { decode<Interest>(d.data!! + ("id" to d.id)) }.getOrNull() }
        }
        registrations += db.collection("matches").whereArrayContains("members", me).addSnapshotListener { snap, _ ->
            _conversations.value = snap?.documents.orEmpty().mapNotNull { d ->
                val members = (d.get("members") as? List<*>)?.filterIsInstance<String>() ?: return@mapNotNull null
                val other = members.firstOrNull { it != me } ?: return@mapNotNull null
                val shared = d.get("contactShared") as? Map<*, *>
                val lastReadAt = (d.get("lastReadAt") as? Map<*, *>)?.get(me) as? Long ?: 0L
                val lastAt = d.getLong("lastMessageAt")
                val unread = if (lastAt != null && lastAt > lastReadAt && d.getString("lastSenderId") != me) 1 else 0
                Conversation(
                    matchId = d.id, otherId = other, createdAt = d.getLong("createdAt") ?: 0L,
                    lastMessage = d.getString("lastMessage"), lastMessageAt = lastAt, unread = unread,
                    iSharedContact = shared?.get(me) == true, theySharedContact = shared?.get(other) == true,
                    closed = d.getBoolean("closed") == true,
                )
            }.sortedByDescending { it.lastMessageAt ?: it.createdAt }
        }
        registrations += userDoc.collection("blocks").addSnapshotListener { snap, _ ->
            _blocked.value = snap?.documents.orEmpty().map { it.id }.toSet()
        }
        registrations += userDoc.collection("shortlist").addSnapshotListener { snap, _ ->
            _shortlist.value = snap?.documents.orEmpty().map { it.id }.toSet()
        }
        registrations += db.collection("reports").whereEqualTo("reporterId", me).addSnapshotListener { snap, _ ->
            _reports.value = snap?.documents.orEmpty().mapNotNull { d -> runCatching { decode<Report>(d.data!! + ("id" to d.id)) }.getOrNull() }
        }
        registrations += userDoc.collection("familyManagers").addSnapshotListener { snap, _ ->
            _managers.value = snap?.documents.orEmpty().mapNotNull { d -> runCatching { decode<FamilyManager>(d.data!! + ("id" to d.id)) }.getOrNull() }
        }
        registrations += userDoc.collection("familyActivity").orderBy("at", Query.Direction.DESCENDING).limit(50).addSnapshotListener { snap, _ ->
            _activity.value = snap?.documents.orEmpty().mapNotNull { d -> runCatching { decode<FamilyActivity>(d.data!!) }.getOrNull() }
        }
    }

    // ---------------------------------------------------------------------------------- Profile

    override suspend fun saveMyProfile(profile: Profile): Result<Profile> = runCatchingCall {
        val res = call("publishProfile", mapOf("profile" to encode(profile)))
        decode<Profile>(res["profile"])
    }

    override suspend fun getProfile(id: String): Profile? {
        if (id == uid) return _myProfile.value
        synchronized(profileCache) { profileCache[id] }?.let { return it }
        return runCatching { decode<Profile>(call("getProfileCard", mapOf("id" to id))["profile"]) }
            .getOrNull()?.also { synchronized(profileCache) { profileCache[id] = it } }
    }

    private val storage by lazy { FirebaseStorage.getInstance() }

    /** Uploads to the member's own Storage folder (rules: owner-only, JPEG, < 8 MB), then publishes. */
    override suspend fun addPhoto(jpeg: ByteArray): Result<Photo> = runCatchingCall {
        val me = uid ?: error("Please sign in again")
        val profile = _myProfile.value ?: error("Create your profile first")
        require(profile.photos.size < MAX_PHOTOS) { "You can add up to $MAX_PHOTOS photos" }
        val photo = Photo(id = "ph_" + UUID.randomUUID().toString().replace("-", "").take(16), seed = (0..Int.MAX_VALUE).random())
        val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
        storage.reference.child("photos/$me/${photo.id}.jpg").putBytes(jpeg, metadata).await()
        photoStore.write(photo.id, jpeg)
        saveMyProfile(profile.copy(photos = profile.photos + photo)).getOrThrow()
        photo
    }

    override suspend fun removePhoto(id: String): Result<Unit> = runCatchingCall {
        val me = uid ?: error("Please sign in again")
        val profile = _myProfile.value ?: error("Create your profile first")
        saveMyProfile(profile.copy(photos = profile.photos.filterNot { it.id == id })).getOrThrow()
        runCatching { storage.reference.child("photos/$me/$id.jpg").delete().await() }
        photoStore.delete(id)
    }

    override suspend fun setPrimaryPhoto(id: String): Result<Unit> = runCatchingCall {
        val profile = _myProfile.value ?: error("Create your profile first")
        val target = profile.photos.firstOrNull { it.id == id } ?: error("Photo not found")
        saveMyProfile(profile.copy(photos = listOf(target) + profile.photos.filterNot { it.id == id })).getOrThrow()
        Unit
    }

    // ---------------------------------------------------------------------------------- Matches

    override val learnedInsight: StateFlow<String?> = settingsRepository.settings
        .map { learner.explain(FactorWeights.fromStored(it.learnedWeights)) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private fun engine() = CompatibilityEngine(FactorWeights.fromStored(settingsRepository.settings.value.learnedWeights))

    /** The server filters (eligibility, blocks, visibility, rate limits) and orders; the client explains. */
    private fun rank(profiles: List<Profile>): List<RankedMatch> {
        val me = _myProfile.value ?: return emptyList()
        val e = engine()
        synchronized(profileCache) { profiles.forEach { profileCache[it.id] = it } }
        return profiles.mapIndexed { i, p ->
            RankedMatch(p, e.score(me, p), (profiles.size - i).toDouble())
        }
    }

    override suspend fun dailyPicks(): List<RankedMatch> = runCatching {
        val res = call("getDailyPicks", mapOf("count" to settingsRepository.settings.value.dailyPicksCount, "weights" to JSONObject(settingsRepository.settings.value.learnedWeights)))
        rank(decode<List<Profile>>(res["profiles"]))
    }.getOrDefault(emptyList())

    override suspend fun search(filters: SearchFilters): List<RankedMatch> = runCatching {
        rank(decode<List<Profile>>(call("searchProfiles", mapOf("filters" to encode(filters)))["profiles"]))
    }.getOrDefault(emptyList())

    override suspend fun compatibilityWith(otherId: String): CompatibilityResult? {
        val me = _myProfile.value ?: return null
        val other = getProfile(otherId) ?: return null
        return engine().score(me, other)
    }

    override suspend fun recordFeedback(otherId: String, feedback: PreferenceLearner.Feedback) {
        val c = compatibilityWith(otherId) ?: return
        settingsRepository.update { s ->
            s.copy(learnedWeights = learner.update(FactorWeights.fromStored(s.learnedWeights), c.factors, feedback).toStored())
        }
    }

    override suspend fun toggleShortlist(otherId: String): Result<Unit> = runCatchingCall {
        val me = uid ?: error("Please sign in again")
        val ref = db.collection("users").document(me).collection("shortlist").document(otherId)
        if (otherId in _shortlist.value) ref.delete().await() else {
            ref.set(mapOf("at" to System.currentTimeMillis())).await()
            recordFeedback(otherId, PreferenceLearner.Feedback.SHORTLISTED)
        }
        Unit
    }

    override suspend fun pass(otherId: String) {
        runCatching { call("passProfile", mapOf("otherId" to otherId)) }
        recordFeedback(otherId, PreferenceLearner.Feedback.PASSED)
    }

    override suspend fun resetLearning() {
        settingsRepository.update { it.copy(learnedWeights = emptyMap()) }
    }

    // -------------------------------------------------------------------------------- Interests

    override suspend fun sendInterest(toId: String, note: String?): Result<Interest> = runCatchingCall {
        val res = call("sendInterest", mapOf("toId" to toId, "note" to note))
        recordFeedback(toId, PreferenceLearner.Feedback.INTEREST_SENT)
        decode<Interest>(res["interest"])
    }

    override suspend fun accept(interestId: String): Result<String> = runCatchingCall {
        call("respondToInterest", mapOf("interestId" to interestId, "action" to "accept"))["matchId"] as String
    }

    override suspend fun decline(interestId: String, message: String?): Result<Unit> = runCatchingCall<Unit> {
        call("respondToInterest", mapOf("interestId" to interestId, "action" to "decline", "message" to message))
    }

    override suspend fun withdraw(interestId: String): Result<Unit> = runCatchingCall<Unit> {
        call("respondToInterest", mapOf("interestId" to interestId, "action" to "withdraw"))
    }

    // ------------------------------------------------------------------------------------- Chat

    override fun messages(matchId: String): Flow<List<Message>> = callbackFlow {
        val reg = db.collection("matches").document(matchId).collection("messages")
            .orderBy("sentAt").limitToLast(200)
            .addSnapshotListener { snap, _ ->
                trySend(snap?.documents.orEmpty().mapNotNull { d ->
                    runCatching { decode<Message>(d.data!! + mapOf("id" to d.id, "matchId" to matchId)) }.getOrNull()
                })
            }
        awaitClose { reg.remove() }
    }

    override suspend fun sendMessage(matchId: String, text: String): Result<Unit> = runCatchingCall<Unit> {
        val me = uid ?: error("Not signed in")
        require(text.isNotBlank() && text.length <= 2000) { "Messages can be up to 2,000 characters" }
        // Rules check: sender is the caller, caller is a member, match is open, size limit, timestamp sanity.
        db.collection("matches").document(matchId).collection("messages")
            .add(mapOf("senderId" to me, "text" to text.trim(), "sentAt" to System.currentTimeMillis()))
            .await()
    }

    override suspend fun setContactShared(matchId: String, shared: Boolean): Result<Unit> = runCatchingCall<Unit> {
        call("setContactSharing", mapOf("matchId" to matchId, "shared" to shared))
    }

    override suspend fun markRead(matchId: String) {
        val me = uid ?: return
        runCatching {
            db.collection("matches").document(matchId).update("lastReadAt.$me", System.currentTimeMillis()).await()
        }
    }

    override suspend fun sharedContact(matchId: String): String? =
        runCatching { call("getSharedContact", mapOf("matchId" to matchId))["phone"] as? String }.getOrNull()

    override suspend fun closeConversation(matchId: String): Result<Unit> = runCatchingCall<Unit> {
        call("closeMatch", mapOf("matchId" to matchId))
    }

    // --------------------------------------------------------------------------------------- AI
    // Every AI call is consent-gated on the server too; on failure we fall back to the offline templates.

    override val aiEnabled: StateFlow<Boolean> = settingsRepository.settings
        .map { it.hasConsent(ConsentPurpose.AI_ASSISTANCE) }
        .stateIn(scope, SharingStarted.Eagerly, false)

    private fun me(fallback: Profile) = _myProfile.value ?: fallback

    override suspend fun matchInsight(other: Profile, compatibility: CompatibilityResult): MatchInsight {
        if (!aiEnabled.value) return DemoAi.insight(me(other), other, compatibility)
        return runCatching { decode<MatchInsight>(call("aiMatchInsight", mapOf("otherId" to other.id))) }
            .getOrElse { DemoAi.insight(me(other), other, compatibility) }
    }

    override suspend fun icebreakers(other: Profile, compatibility: CompatibilityResult): List<String> {
        if (!aiEnabled.value) return DemoAi.icebreakers(me(other), other, compatibility)
        return runCatching { decode<List<String>>(call("aiIcebreakers", mapOf("otherId" to other.id))["suggestions"]) }
            .getOrElse { DemoAi.icebreakers(me(other), other, compatibility) }
    }

    override suspend fun draftBio(profile: Profile, tone: String, language: String): String {
        if (!aiEnabled.value) return DemoAi.bio(profile, tone)
        return runCatching { call("aiDraftBio", mapOf("profile" to encode(profile), "tone" to tone, "language" to language))["bio"] as String }
            .getOrElse { DemoAi.bio(profile, tone) }
    }

    override suspend fun parseSearch(query: String): SearchFilters {
        if (!aiEnabled.value) return DemoAi.parseSearch(query)
        return runCatching { decode<SearchFilters>(call("aiParseSearch", mapOf("query" to query))["filters"]) }
            .getOrElse { DemoAi.parseSearch(query) }
    }

    override suspend fun kindDecline(otherFirstName: String): String {
        if (!aiEnabled.value) return DemoAi.kindDecline(otherFirstName)
        return runCatching { call("aiKindDecline", mapOf("otherFirstName" to otherFirstName))["message"] as String }
            .getOrElse { DemoAi.kindDecline(otherFirstName) }
    }

    override suspend fun translate(text: String, targetLanguage: String): String {
        if (!aiEnabled.value) return DemoAi.translate(text, targetLanguage)
        return runCatching { call("aiTranslate", mapOf("text" to text, "targetLanguage" to targetLanguage))["text"] as String }
            .getOrElse { DemoAi.translate(text, targetLanguage) }
    }

    override suspend fun ask(history: List<AssistantTurn>): String {
        if (!aiEnabled.value) return DemoAi.assistant(history, _myProfile.value, learnedInsight.value)
        val turns = JSONArray(history.takeLast(12).map { JSONObject(mapOf("role" to if (it.fromUser) "user" else "assistant", "text" to it.text)) })
        return runCatching { call("aiAssistant", mapOf("history" to turns))["reply"] as String }
            .getOrElse { DemoAi.assistant(history, _myProfile.value, learnedInsight.value) }
    }

    // ----------------------------------------------------------------------------------- Safety

    override suspend fun report(userId: String, reason: ReportReason, details: String, attachRecentMessages: Boolean): Result<Unit> = runCatchingCall<Unit> {
        call("reportUser", mapOf("userId" to userId, "reason" to reason.name, "details" to details, "attachRecentMessages" to attachRecentMessages))
    }

    override suspend fun block(userId: String): Result<Unit> = runCatchingCall<Unit> {
        call("blockUser", mapOf("userId" to userId))
        synchronized(profileCache) { profileCache.remove(userId) }
    }
    override suspend fun unblock(userId: String): Result<Unit> = runCatchingCall<Unit> { call("unblockUser", mapOf("userId" to userId)) }

    // ---------------------------------------------------------------------------------- Privacy

    override suspend fun setConsent(purpose: ConsentPurpose, granted: Boolean) {
        if (purpose.required) return
        settingsRepository.update { it.copy(consents = it.consents + (purpose to ConsentRecord(granted, System.currentTimeMillis()))) }
        runCatching { call("updateConsent", mapOf("purpose" to purpose.name, "granted" to granted)) }
    }

    override suspend fun exportMyData(): Result<String> = runCatchingCall { call("exportMyData")["json"] as? String ?: "{}" }
    override suspend fun requestDeletion(): Result<Unit> = runCatchingCall {
        call("requestAccountDeletion")
        settingsRepository.update { it.copy(deletionRequestedAt = System.currentTimeMillis()) }
    }
    override suspend fun cancelDeletion(): Result<Unit> = runCatchingCall {
        call("cancelAccountDeletion")
        settingsRepository.update { it.copy(deletionRequestedAt = null) }
    }

    // ----------------------------------------------------------------------------------- Family

    override suspend fun invite(name: String, relation: String, permissions: Set<FamilyPermission>): Result<FamilyManager> = runCatchingCall {
        decode<FamilyManager>(call("inviteFamilyManager", mapOf("name" to name, "relation" to relation, "permissions" to JSONArray(permissions.map { it.name })))["manager"])
    }

    override suspend fun updatePermissions(managerId: String, permissions: Set<FamilyPermission>): Result<Unit> = runCatchingCall<Unit> {
        call("updateFamilyManager", mapOf("managerId" to managerId, "permissions" to JSONArray(permissions.map { it.name })))
    }

    override suspend fun remove(managerId: String): Result<Unit> = runCatchingCall<Unit> { call("removeFamilyManager", mapOf("managerId" to managerId)) }

    private companion object {
        const val MAX_PHOTOS = 6
    }

    // ----------------------------------------------------------------------------- Verification

    override suspend fun submitSelfie(challengePassed: Boolean): Result<Unit> = runCatchingCall<Unit> {
        call("submitSelfieCheck", mapOf("challengePassed" to challengePassed))
    }

    override suspend fun startGovernmentId(): Result<String> = runCatchingCall {
        call("startGovIdVerification")["message"] as? String ?: "Continue in DigiLocker to finish verification."
    }

    override suspend fun sendWorkEmailCode(email: String): Result<Unit> = runCatchingCall<Unit> {
        call("sendWorkEmailCode", mapOf("email" to email))
    }

    override suspend fun confirmWorkEmail(code: String): Result<Unit> = runCatchingCall<Unit> {
        call("confirmWorkEmail", mapOf("code" to code))
    }

    // Registered last so every listener target above is initialised before the first callback.
    init {
        auth.addAuthStateListener { fa ->
            _session.value = fa.currentUser?.let { Session(it.uid, mask(it.phoneNumber)) }
            restartListeners()
        }
        // The server enforces who may contact this member, so keep it in sync with local settings.
        scope.launch {
            combine(
                _session,
                settingsRepository.settings.map { Triple(it.verifiedOnlyContact, it.requirePhotoToContact, it.incognito) }.distinctUntilChanged(),
            ) { s, prefs -> s to prefs }.collect { (s, prefs) ->
                if (s != null) {
                    runCatching {
                        call("updatePrivacySettings", mapOf("verifiedOnlyContact" to prefs.first, "requirePhotoToContact" to prefs.second, "incognito" to prefs.third))
                    }
                }
            }
        }
        // Coarse device-integrity signals feed the account risk score (never a lock-out on their own).
        scope.launch {
            _session.filterNotNull().map { it.userId }.distinctUntilChanged().collect {
                runCatching { call("reportClientSignals", mapOf("deviceSignals" to JSONArray(DeviceIntegrity.signals(appContext).map { s -> s.name }))) }
            }
        }
    }
}
