package com.vachan.app.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class Gender(val label: String) { FEMALE("Woman"), MALE("Man") }

/** Legal minimum marriageable age in India (Prohibition of Child Marriage Act, 2006). */
fun Gender.minimumMarriageAge(): Int = when (this) {
    Gender.FEMALE -> 18
    Gender.MALE -> 21
}

@Serializable
enum class MaritalStatus(val label: String) {
    NEVER_MARRIED("Never married"),
    DIVORCED("Divorced"),
    AWAITING_DIVORCE("Awaiting divorce"),
    WIDOWED("Widowed"),
    ANNULLED("Annulled"),
}

@Serializable
enum class Religion(val label: String) {
    HINDU("Hindu"), MUSLIM("Muslim"), CHRISTIAN("Christian"), SIKH("Sikh"), JAIN("Jain"),
    BUDDHIST("Buddhist"), PARSI("Parsi"), JEWISH("Jewish"), SPIRITUAL("Spiritual"),
    NO_RELIGION("No religion"), OTHER("Other"),
}

@Serializable
enum class Diet(val label: String) {
    VEGETARIAN("Vegetarian"), EGGETARIAN("Eggetarian"), NON_VEGETARIAN("Non-vegetarian"),
    VEGAN("Vegan"), JAIN("Jain food"),
}

@Serializable
enum class Habit(val label: String) { NEVER("Never"), OCCASIONALLY("Occasionally"), REGULARLY("Regularly") }

@Serializable
enum class FamilyType(val label: String) { JOINT("Joint family"), NUCLEAR("Nuclear family"), FLEXIBLE("Flexible") }

@Serializable
enum class ManagedBy(val label: String) {
    SELF("Self"), PARENT("Parent"), SIBLING("Sibling"), RELATIVE("Relative"), FRIEND("Friend"),
}

@Serializable
enum class EducationLevel(val label: String, val rank: Int) {
    HIGH_SCHOOL("High school", 0),
    DIPLOMA("Diploma", 1),
    BACHELORS("Bachelor's", 2),
    MASTERS("Master's", 3),
    PROFESSIONAL("Professional (CA, MBBS, LLB…)", 3),
    DOCTORATE("Doctorate", 4),
}

@Serializable
enum class IncomeBand(val label: String) {
    UNDISCLOSED("Prefer not to say"),
    UPTO_5L("Up to ₹5 L"),
    L5_10("₹5–10 L"),
    L10_20("₹10–20 L"),
    L20_35("₹20–35 L"),
    L35_50("₹35–50 L"),
    L50_100("₹50 L–1 Cr"),
    ABOVE_1CR("Above ₹1 Cr"),
}

@Serializable
enum class PhotoVisibility(val label: String, val description: String) {
    ALL_MEMBERS("All members", "Any signed-in member can see your photos"),
    VERIFIED_MEMBERS("Verified members", "Only members who completed verification"),
    AFTER_ACCEPT("After I accept", "Others see a blurred preview until you accept their interest"),
}

@Serializable
enum class ProfileVisibility(val label: String, val description: String) {
    EVERYONE("Everyone", "Shown in matches and search"),
    VERIFIED_ONLY("Verified members only", "Only verified members can find or contact you"),
    HIDDEN("Hidden", "You won't appear anywhere; existing chats continue"),
}

@Serializable
enum class ActivityBucket(val label: String) {
    TODAY("Active today"), THIS_WEEK("Active this week"), THIS_MONTH("Active this month"), EARLIER("Active a while ago"),
}

@Serializable
data class Location(
    val city: String,
    val state: String,
    val country: String = "India",
    /** City-centroid coordinates only; we never store or show a precise location. */
    val lat: Double,
    val lng: Double,
)

@Serializable
data class Verification(
    val phone: Boolean = false,
    val selfie: Boolean = false,
    val governmentId: Boolean = false,
    val workEmail: Boolean = false,
) {
    /** A member is "verified" once a human face (selfie liveness) or an official ID is confirmed. */
    val isVerified: Boolean get() = selfie || governmentId
    val count: Int get() = listOf(phone, selfie, governmentId, workEmail).count { it }
}

@Serializable
data class Photo(
    val id: String,
    /** Remote URL when backed by Firebase; null in Demo mode where an illustrated avatar is drawn. */
    val url: String? = null,
    /** Seed for the illustrated placeholder avatar. */
    val seed: Int = 0,
)

/**
 * The Values Compass: questions about the things that most often break marriages.
 * Answers are ordinal (0..4) between two poles so that distance is meaningful.
 */
@Serializable
enum class ValueQuestion(val prompt: String, val options: List<String>, val topic: String) {
    LIVING_ARRANGEMENT(
        "After marriage I'd like to live…",
        listOf("With my parents", "Near family", "Flexible", "On our own", "Wherever work takes us"),
        "where you'll live",
    ),
    CAREERS(
        "Both partners pursuing careers is…",
        listOf("Not important", "Nice to have", "Depends", "Important", "Essential"),
        "careers after marriage",
    ),
    CHILDREN(
        "Children…",
        listOf("Want soon", "Want in a few years", "Open to it", "Probably not", "Don't want"),
        "children",
    ),
    RELOCATION(
        "Moving to another city or country for opportunities…",
        listOf("Not open", "Reluctant", "Depends", "Open", "Excited about it"),
        "relocation",
    ),
    FAITH(
        "Religion in daily life is…",
        listOf("Central", "Important", "Occasional", "Cultural only", "Not part of it"),
        "faith and rituals",
    ),
    FINANCES(
        "Money in marriage should be…",
        listOf("Fully joint", "Mostly joint", "Mix of both", "Mostly separate", "Fully separate"),
        "managing money",
    ),
    HOUSEHOLD(
        "Household responsibilities…",
        listOf("Traditional roles", "Mostly traditional", "Whoever has time", "Mostly shared", "Equally shared"),
        "sharing the household",
    ),
    PARENTS_ROLE(
        "Our parents' role in big decisions…",
        listOf("They decide", "Strong say", "Advisory", "Informed", "Not involved"),
        "family involvement",
    ),
    SOCIAL_LIFE(
        "My ideal weekend…",
        listOf("Quiet at home", "Small family time", "Mix", "Out with friends", "Travel and adventure"),
        "weekends and social life",
    ),
    CONFLICT(
        "When we disagree, I prefer to…",
        listOf("Talk it out immediately", "Talk the same day", "Depends", "Take some space first", "Let it cool off for a while"),
        "handling disagreements",
    ),
    MENTAL_HEALTH(
        "Talking about mental health and therapy…",
        listOf("Private matter", "Rarely", "When needed", "Comfortable", "Very open"),
        "emotional wellbeing",
    ),
    SPENDING(
        "My spending style…",
        listOf("Big saver", "Saver", "Balanced", "Spender", "Live in the moment"),
        "spending and saving",
    ),
}

/** importance: 0 = doesn't matter, 1 = somewhat, 2 = important, 3 = very important. */
@Serializable
data class ValueAnswer(val answer: Int, val importance: Int)

@Serializable
data class PartnerPreferences(
    val minAge: Int = 21,
    val maxAge: Int = 40,
    val minHeightCm: Int? = null,
    val maxHeightCm: Int? = null,
    val maritalStatuses: Set<MaritalStatus> = setOf(MaritalStatus.NEVER_MARRIED),
    /** Empty means "doesn't matter". */
    val religions: Set<Religion> = emptySet(),
    val motherTongues: Set<String> = emptySet(),
    val diets: Set<Diet> = emptySet(),
    val minEducation: EducationLevel? = null,
    val maxDistanceKm: Int? = null,
    val countries: Set<String> = emptySet(),
    val acceptsSmoking: Boolean = true,
    val acceptsDrinking: Boolean = true,
    val acceptsChildren: Boolean = true,
    /** Preferences the user marked as non-negotiable; these become hard filters. */
    val mustHaves: Set<PreferenceKey> = emptySet(),
)

@Serializable
enum class PreferenceKey(val label: String) {
    AGE("Age"), HEIGHT("Height"), MARITAL_STATUS("Marital status"), RELIGION("Religion"),
    MOTHER_TONGUE("Mother tongue"), DIET("Diet"), EDUCATION("Education"), DISTANCE("Distance"),
    COUNTRY("Country"), SMOKING("Smoking"), DRINKING("Drinking"), CHILDREN("Children"),
}

@Serializable
data class Profile(
    val id: String,
    val firstName: String,
    val lastInitial: String,
    val gender: Gender,
    val age: Int,
    val heightCm: Int,
    val location: Location,
    val religion: Religion? = null,
    val motherTongue: String,
    /** Optional and hidden from others by default. */
    val community: String? = null,
    val showCommunity: Boolean = false,
    val maritalStatus: MaritalStatus = MaritalStatus.NEVER_MARRIED,
    val hasChildren: Boolean = false,
    val education: EducationLevel,
    val educationField: String = "",
    val profession: String,
    val incomeBand: IncomeBand = IncomeBand.UNDISCLOSED,
    val showIncome: Boolean = false,
    val diet: Diet,
    val smoking: Habit = Habit.NEVER,
    val drinking: Habit = Habit.NEVER,
    val familyType: FamilyType = FamilyType.FLEXIBLE,
    val managedBy: ManagedBy = ManagedBy.SELF,
    val about: String = "",
    val interests: Set<String> = emptySet(),
    val languages: Set<String> = emptySet(),
    val values: Map<ValueQuestion, ValueAnswer> = emptyMap(),
    val openToRelocate: Boolean = false,
    val residencyStatus: String? = null,
    val verification: Verification = Verification(phone = true),
    val photos: List<Photo> = emptyList(),
    val photoVisibility: PhotoVisibility = PhotoVisibility.VERIFIED_MEMBERS,
    val visibility: ProfileVisibility = ProfileVisibility.EVERYONE,
    /** Share of received interests answered within 7 days, 0..1. */
    val responseRate: Float? = null,
    val activity: ActivityBucket = ActivityBucket.THIS_WEEK,
    val memberCode: String = "",
    val preferences: PartnerPreferences = PartnerPreferences(),
) {
    val displayName: String get() = "$firstName ${lastInitial.take(1)}."
    val isNri: Boolean get() = !location.country.equals("India", ignoreCase = true)
    val seeking: Gender get() = if (gender == Gender.FEMALE) Gender.MALE else Gender.FEMALE

    /** 0..100: how complete the profile is (used by the profile coach). */
    fun completeness(): Int {
        val checks = listOf(
            about.length >= 80,
            interests.size >= 3,
            values.size >= 8,
            photos.isNotEmpty(),
            verification.isVerified,
            educationField.isNotBlank(),
            languages.isNotEmpty(),
            preferences.mustHaves.isNotEmpty() || preferences.religions.isNotEmpty() || preferences.maxDistanceKm != null,
        )
        return 20 + (checks.count { it } * 80 / checks.size)
    }
}

fun heightLabel(cm: Int): String {
    val totalInches = (cm / 2.54).toInt()
    return "${totalInches / 12}′${totalInches % 12}″ ($cm cm)"
}
