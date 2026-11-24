package com.vachan.app.core.matching

import com.vachan.app.core.model.Diet
import com.vachan.app.core.model.FamilyType
import com.vachan.app.core.model.Habit
import com.vachan.app.core.model.PartnerPreferences
import com.vachan.app.core.model.PreferenceKey
import com.vachan.app.core.model.Profile
import com.vachan.app.core.model.ProfileVisibility
import com.vachan.app.core.model.ValueQuestion
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Deterministic, explainable, two-way compatibility.
 *
 * The score for a pair is the harmonic mean of A→B and B→A, so a match only ranks highly when it
 * works for *both* people. Each direction combines how well the other person fits what you're
 * looking for (directional) with shared factors such as values and lifestyle (symmetric).
 *
 * This is the source of truth for ranking. Language models never decide who is shown; they only
 * explain the factors computed here. The same algorithm is mirrored server-side in
 * `firebase/functions/src/matching/engine.ts`.
 */
class CompatibilityEngine(private val weights: FactorWeights = FactorWeights()) {

    fun withWeights(newWeights: FactorWeights) = CompatibilityEngine(newWeights)

    /** Policy and must-have checks. Returns false if [candidate] must not be shown to [viewer]. */
    fun isEligible(viewer: Profile, candidate: Profile, blockedIds: Set<String> = emptySet()): Boolean {
        if (viewer.id == candidate.id) return false
        if (candidate.id in blockedIds) return false
        if (candidate.gender != viewer.seeking || viewer.gender != candidate.seeking) return false
        when (candidate.visibility) {
            ProfileVisibility.HIDDEN -> return false
            ProfileVisibility.VERIFIED_ONLY -> if (!viewer.verification.isVerified) return false
            ProfileVisibility.EVERYONE -> Unit
        }
        return passesMustHaves(viewer.preferences, viewer, candidate) &&
            passesMustHaves(candidate.preferences, candidate, viewer)
    }

    fun score(a: Profile, b: Profile): CompatibilityResult {
        val aToB = preferenceFit(a, b)
        val bToA = preferenceFit(b, a)
        val shared = sharedFactors(a, b)

        val directionalA = combine(aToB.score, shared)
        val directionalB = combine(bToA.score, shared)
        val reciprocal = harmonicMean(directionalA, directionalB)

        val factors = buildList {
            add(
                FactorScore(
                    Factor.PREFERENCES,
                    harmonicMean(aToB.score, bToA.score),
                    preferenceDetail(aToB, bToA),
                ),
            )
            addAll(shared)
        }

        val frictions = buildList {
            aToB.misses.forEach { add("Outside your preference: $it") }
            bToA.misses.forEach { add("Outside their preference: $it") }
            addAll(valueFrictions(a, b))
        }

        return CompatibilityResult(
            score = (reciprocal * 100).roundToInt().coerceIn(0, 100),
            youFitThem = (bToA.score * 100).roundToInt(),
            theyFitYou = (aToB.score * 100).roundToInt(),
            factors = factors.sortedByDescending { it.score * weights.of(it.factor) },
            highlights = highlights(a, b, shared),
            frictions = frictions,
            sharedInterests = (a.interests intersect b.interests).sorted(),
        )
    }

    // ---------------------------------------------------------------- directional preference fit

    internal data class PreferenceFit(val score: Double, val matched: Int, val total: Int, val misses: List<String>)

    internal fun preferenceFit(seeker: Profile, candidate: Profile): PreferenceFit {
        val dims = dimensionScores(seeker.preferences, seeker, candidate)
        if (dims.isEmpty()) return PreferenceFit(0.75, 0, 0, emptyList())
        val totalWeight = dims.sumOf { it.weight }
        val score = dims.sumOf { it.weight * it.score } / totalWeight
        return PreferenceFit(
            score = score,
            matched = dims.count { it.score >= 0.99 },
            total = dims.size,
            misses = dims.filter { it.score < 0.5 }.map { it.missLabel },
        )
    }

    private data class Dim(val key: PreferenceKey, val weight: Double, val score: Double, val missLabel: String)

    private fun dimensionScores(p: PartnerPreferences, seeker: Profile, c: Profile): List<Dim> = buildList {
        // Age is always specified.
        val ageScore = when {
            c.age in p.minAge..p.maxAge -> 1.0
            c.age < p.minAge -> (1.0 - (p.minAge - c.age) / 5.0).coerceAtLeast(0.0)
            else -> (1.0 - (c.age - p.maxAge) / 5.0).coerceAtLeast(0.0)
        }
        add(Dim(PreferenceKey.AGE, 3.0, ageScore, "age ${c.age} (looking for ${p.minAge}–${p.maxAge})"))

        if (p.minHeightCm != null || p.maxHeightCm != null) {
            val min = p.minHeightCm ?: 0
            val max = p.maxHeightCm ?: 300
            val s = when {
                c.heightCm in min..max -> 1.0
                c.heightCm < min -> (1.0 - (min - c.heightCm) / 10.0).coerceAtLeast(0.0)
                else -> (1.0 - (c.heightCm - max) / 10.0).coerceAtLeast(0.0)
            }
            add(Dim(PreferenceKey.HEIGHT, 1.0, s, "height ${c.heightCm} cm"))
        }

        if (p.maritalStatuses.isNotEmpty()) {
            val s = if (c.maritalStatus in p.maritalStatuses) 1.0 else 0.0
            add(Dim(PreferenceKey.MARITAL_STATUS, 2.0, s, c.maritalStatus.label.lowercase()))
        }

        if (p.religions.isNotEmpty()) {
            val s = if (c.religion != null && c.religion in p.religions) 1.0 else 0.0
            add(Dim(PreferenceKey.RELIGION, 2.0, s, "religion"))
        }

        if (p.motherTongues.isNotEmpty()) {
            val tongues = p.motherTongues.map { it.lowercase() }.toSet()
            val s = when {
                c.motherTongue.lowercase() in tongues -> 1.0
                c.languages.any { it.lowercase() in tongues } -> 0.6
                else -> 0.0
            }
            add(Dim(PreferenceKey.MOTHER_TONGUE, 1.5, s, "mother tongue ${c.motherTongue}"))
        }

        if (p.diets.isNotEmpty()) {
            val s = if (c.diet in p.diets) 1.0 else p.diets.maxOf { dietCompatibility(it, c.diet) }
            add(Dim(PreferenceKey.DIET, 1.5, s, c.diet.label.lowercase()))
        }

        p.minEducation?.let { min ->
            val gap = min.rank - c.education.rank
            val s = when {
                gap <= 0 -> 1.0
                gap == 1 -> 0.5
                else -> 0.0
            }
            add(Dim(PreferenceKey.EDUCATION, 1.0, s, "education (${c.education.label})"))
        }

        p.maxDistanceKm?.let { max ->
            val d = distanceKm(seeker, c)
            var s = if (d <= max) 1.0 else (1.0 - (d - max) / max.toDouble()).coerceAtLeast(0.0)
            if (c.openToRelocate || seeker.openToRelocate) s = maxOf(s, 0.6)
            add(Dim(PreferenceKey.DISTANCE, 1.5, s, "${d.roundToInt()} km away"))
        }

        if (p.countries.isNotEmpty()) {
            val inCountry = p.countries.any { it.equals(c.location.country, ignoreCase = true) }
            val s = when {
                inCountry -> 1.0
                c.openToRelocate -> 0.5
                else -> 0.0
            }
            add(Dim(PreferenceKey.COUNTRY, 1.5, s, "lives in ${c.location.country}"))
        }

        if (!p.acceptsSmoking) {
            add(Dim(PreferenceKey.SMOKING, 1.0, habitTolerance(c.smoking), "smokes ${c.smoking.label.lowercase()}"))
        }
        if (!p.acceptsDrinking) {
            add(Dim(PreferenceKey.DRINKING, 1.0, habitTolerance(c.drinking), "drinks ${c.drinking.label.lowercase()}"))
        }
        if (!p.acceptsChildren) {
            add(Dim(PreferenceKey.CHILDREN, 2.0, if (c.hasChildren) 0.0 else 1.0, "has children"))
        }
    }

    private fun passesMustHaves(p: PartnerPreferences, seeker: Profile, candidate: Profile): Boolean {
        if (p.mustHaves.isEmpty()) return true
        val dims = dimensionScores(p, seeker, candidate).associateBy { it.key }
        return p.mustHaves.all { key -> (dims[key]?.score ?: 1.0) >= 0.99 }
    }

    // ------------------------------------------------------------------------------ shared factors

    private fun sharedFactors(a: Profile, b: Profile): List<FactorScore> = buildList {
        valuesScore(a, b)?.let { add(it) }
        add(lifestyleScore(a, b))
        interestsScore(a, b)?.let { add(it) }
        add(familyScore(a, b))
        add(languageScore(a, b))
        add(locationScore(a, b))
    }

    private fun valuesScore(a: Profile, b: Profile): FactorScore? {
        val shared = ValueQuestion.entries.mapNotNull { q ->
            val x = a.values[q] ?: return@mapNotNull null
            val y = b.values[q] ?: return@mapNotNull null
            Triple(q, x, y)
        }
        if (shared.size < 3) return null
        var weighted = 0.0
        var totalWeight = 0.0
        var aligned = 0
        for ((_, x, y) in shared) {
            val w = 0.25 + (x.importance + y.importance) / 2.0
            val sim = 1.0 - abs(x.answer - y.answer) / 4.0
            if (abs(x.answer - y.answer) <= 1) aligned++
            weighted += w * sim
            totalWeight += w
        }
        return FactorScore(Factor.VALUES, weighted / totalWeight, "Aligned on $aligned of ${shared.size} life questions")
    }

    private fun valueFrictions(a: Profile, b: Profile): List<String> = ValueQuestion.entries.mapNotNull { q ->
        val x = a.values[q] ?: return@mapNotNull null
        val y = b.values[q] ?: return@mapNotNull null
        if (minOf(x.importance, y.importance) >= 2 && abs(x.answer - y.answer) >= 3) {
            "Different views on ${q.topic} — worth an honest conversation"
        } else {
            null
        }
    }

    private fun lifestyleScore(a: Profile, b: Profile): FactorScore {
        val diet = dietCompatibility(a.diet, b.diet)
        val smoke = 1.0 - abs(a.smoking.ordinal - b.smoking.ordinal) / 2.0
        val drink = 1.0 - abs(a.drinking.ordinal - b.drinking.ordinal) / 2.0
        val score = diet * 0.5 + smoke * 0.25 + drink * 0.25
        val detail = buildList {
            if (a.diet == b.diet) add("both ${a.diet.label.lowercase()}")
            if (a.smoking == Habit.NEVER && b.smoking == Habit.NEVER) add("neither smokes")
            if (a.drinking == b.drinking) add("same view on drinking")
        }.joinToString(", ").ifEmpty { "Different everyday habits" }.replaceFirstChar(Char::uppercase)
        return FactorScore(Factor.LIFESTYLE, score, detail)
    }

    private fun interestsScore(a: Profile, b: Profile): FactorScore? {
        if (a.interests.isEmpty() || b.interests.isEmpty()) return null
        val shared = a.interests intersect b.interests
        val union = a.interests union b.interests
        val jaccard = shared.size.toDouble() / union.size
        val score = (minOf(1.0, shared.size / 4.0) * 0.7 + jaccard * 0.3).coerceIn(0.0, 1.0)
        val detail = if (shared.isEmpty()) {
            "No shared interests yet"
        } else {
            "Both enjoy ${shared.sorted().take(3).joinToString(", ")}"
        }
        return FactorScore(Factor.INTERESTS, score, detail)
    }

    private fun familyScore(a: Profile, b: Profile): FactorScore {
        val s = when {
            a.familyType == b.familyType -> 1.0
            a.familyType == FamilyType.FLEXIBLE || b.familyType == FamilyType.FLEXIBLE -> 0.8
            else -> 0.3
        }
        val detail = if (a.familyType == b.familyType) {
            "Both prefer a ${a.familyType.label.lowercase()}"
        } else {
            "${a.familyType.label} and ${b.familyType.label.lowercase()}"
        }
        return FactorScore(Factor.FAMILY, s, detail)
    }

    private fun languageScore(a: Profile, b: Profile): FactorScore {
        val aLangs = (a.languages + a.motherTongue).map { it.lowercase() }.toSet()
        val bLangs = (b.languages + b.motherTongue).map { it.lowercase() }.toSet()
        val common = aLangs intersect bLangs
        val sameTongue = a.motherTongue.equals(b.motherTongue, ignoreCase = true)
        val score = when {
            sameTongue -> 1.0
            common.isNotEmpty() -> 0.8
            else -> 0.3
        }
        val detail = when {
            sameTongue -> "Same mother tongue (${a.motherTongue})"
            common.isNotEmpty() -> "Both speak ${common.first().replaceFirstChar(Char::uppercase)}"
            else -> "No common language yet — translation can help"
        }
        return FactorScore(Factor.LANGUAGE, score, detail)
    }

    private fun locationScore(a: Profile, b: Profile): FactorScore {
        val d = distanceKm(a, b)
        val sameCountry = a.location.country.equals(b.location.country, ignoreCase = true)
        var score = when {
            a.location.city.equals(b.location.city, ignoreCase = true) -> 1.0
            d < 200 -> 0.8
            sameCountry -> 0.6
            else -> 0.3
        }
        val relocates = a.openToRelocate || b.openToRelocate
        if (relocates) score = maxOf(score, 0.7)
        val detail = when {
            a.location.city.equals(b.location.city, ignoreCase = true) -> "Both in ${a.location.city}"
            else -> "${formatKm(d)} apart" + if (relocates) " · open to relocating" else ""
        }
        return FactorScore(Factor.LOCATION, score, detail)
    }

    private fun highlights(a: Profile, b: Profile, shared: List<FactorScore>): List<String> = buildList {
        ValueQuestion.entries.forEach { q ->
            val x = a.values[q]
            val y = b.values[q]
            if (x != null && y != null && x.answer == y.answer && minOf(x.importance, y.importance) >= 2) {
                add("You agree on ${q.topic}")
            }
        }
        shared.filter { it.score >= 0.8 && it.factor != Factor.VALUES }.forEach { add(it.detail) }
    }.distinct().take(4)

    // ------------------------------------------------------------------------------------- helpers

    private fun combine(preference: Double, shared: List<FactorScore>): Double {
        val parts = listOf(Factor.PREFERENCES to preference) + shared.map { it.factor to it.score }
        val totalWeight = parts.sumOf { weights.of(it.first) }
        return parts.sumOf { weights.of(it.first) * it.second } / totalWeight
    }

    private fun preferenceDetail(aToB: PreferenceFit, bToA: PreferenceFit): String {
        val them = if (bToA.total == 0) "no specific preferences" else "${bToA.matched}/${bToA.total} of their preferences"
        val you = if (aToB.total == 0) "you have no specific preferences" else "they fit ${aToB.matched}/${aToB.total} of yours"
        return "You fit $them; $you"
    }

    companion object {
        fun harmonicMean(x: Double, y: Double): Double = if (x + y == 0.0) 0.0 else 2 * x * y / (x + y)

        fun dietCompatibility(a: Diet, b: Diet): Double {
            if (a == b) return 1.0
            val vegFamily = setOf(Diet.VEGETARIAN, Diet.VEGAN, Diet.JAIN, Diet.EGGETARIAN)
            return when {
                a in vegFamily && b in vegFamily -> if (Diet.EGGETARIAN in setOf(a, b)) 0.75 else 0.85
                a == Diet.NON_VEGETARIAN && b == Diet.EGGETARIAN || b == Diet.NON_VEGETARIAN && a == Diet.EGGETARIAN -> 0.6
                setOf(a, b).any { it == Diet.JAIN || it == Diet.VEGAN } -> 0.2
                else -> 0.4
            }
        }

        private fun habitTolerance(h: Habit) = when (h) {
            Habit.NEVER -> 1.0
            Habit.OCCASIONALLY -> 0.4
            Habit.REGULARLY -> 0.0
        }

        fun distanceKm(a: Profile, b: Profile): Double {
            val r = 6371.0
            val dLat = Math.toRadians(b.location.lat - a.location.lat)
            val dLng = Math.toRadians(b.location.lng - a.location.lng)
            val h = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(a.location.lat)) * cos(Math.toRadians(b.location.lat)) * sin(dLng / 2) * sin(dLng / 2)
            return 2 * r * asin(sqrt(h))
        }

        fun formatKm(d: Double): String = when {
            d < 10 -> "Under 10 km"
            d < 1000 -> "${d.roundToInt()} km"
            else -> "%,d km".format(d.roundToInt())
        }
    }
}

enum class Factor(val label: String, val defaultWeight: Double) {
    PREFERENCES("Fits what you both want", 0.35),
    VALUES("Life values", 0.25),
    LIFESTYLE("Lifestyle", 0.12),
    INTERESTS("Interests", 0.10),
    FAMILY("Family", 0.06),
    LANGUAGE("Language", 0.06),
    LOCATION("Location", 0.06),
}

/** Factor weights, optionally personalised by [PreferenceLearner] via bounded multipliers. */
data class FactorWeights(val multipliers: Map<Factor, Double> = emptyMap()) {
    fun of(f: Factor): Double = f.defaultWeight * (multipliers[f] ?: 1.0).coerceIn(MIN_MULTIPLIER, MAX_MULTIPLIER)

    companion object {
        const val MIN_MULTIPLIER = 0.5
        const val MAX_MULTIPLIER = 2.0

        fun fromStored(stored: Map<String, Double>) = FactorWeights(
            stored.mapNotNull { (k, v) -> Factor.entries.firstOrNull { it.name == k }?.let { it to v } }.toMap(),
        )
    }

    fun toStored(): Map<String, Double> = multipliers.mapKeys { it.key.name }
}

data class FactorScore(val factor: Factor, val score: Double, val detail: String)

data class CompatibilityResult(
    /** Two-way score, 0..100. */
    val score: Int,
    /** How well *you* fit *their* stated preferences, 0..100. */
    val youFitThem: Int,
    /** How well *they* fit *your* stated preferences, 0..100. */
    val theyFitYou: Int,
    val factors: List<FactorScore>,
    val highlights: List<String>,
    val frictions: List<String>,
    val sharedInterests: List<String>,
) {
    val label: String
        get() = when {
            score >= 85 -> "Exceptional match"
            score >= 72 -> "Strong match"
            score >= 60 -> "Good match"
            score >= 45 -> "Some common ground"
            else -> "Low compatibility"
        }
}
