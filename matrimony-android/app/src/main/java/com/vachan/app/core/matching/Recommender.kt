package com.vachan.app.core.matching

import com.vachan.app.core.model.ActivityBucket
import com.vachan.app.core.model.Profile

data class RankedMatch(val profile: Profile, val compatibility: CompatibilityResult, val rankScore: Double)

/**
 * Builds the Daily Picks list: a small, diversified set of two-way-compatible people.
 *
 * Rank = compatibility + small trust/responsiveness boosts, then a greedy diversification pass so
 * the list isn't eight near-identical profiles (same city, same profession).
 */
class Recommender(private val engine: CompatibilityEngine) {

    fun dailyPicks(
        viewer: Profile,
        candidates: List<Profile>,
        excludeIds: Set<String>,
        blockedIds: Set<String>,
        count: Int,
    ): List<RankedMatch> {
        val pool = candidates
            .filter { it.id !in excludeIds && engine.isEligible(viewer, it, blockedIds) }
            .map { c ->
                val compat = engine.score(viewer, c)
                RankedMatch(c, compat, compat.score + boost(c))
            }
            .filter { it.compatibility.score >= MIN_SCORE }
            .sortedByDescending { it.rankScore }
            .toMutableList()

        val picked = mutableListOf<RankedMatch>()
        while (picked.size < count && pool.isNotEmpty()) {
            val best = pool.maxBy { it.rankScore - diversityPenalty(it.profile, picked) }
            picked += best
            pool.remove(best)
        }
        return picked
    }

    fun rankSearchResults(viewer: Profile, results: List<Profile>, blockedIds: Set<String>): List<RankedMatch> =
        results.filter { engine.isEligible(viewer, it, blockedIds) }
            .map { RankedMatch(it, engine.score(viewer, it), 0.0) }
            .map { it.copy(rankScore = it.compatibility.score + boost(it.profile)) }
            .sortedByDescending { it.rankScore }

    private fun boost(p: Profile): Double {
        var b = 0.0
        if (p.verification.isVerified) b += 4
        if (p.verification.governmentId) b += 1
        p.responseRate?.let { b += it * 3 }
        b += when (p.activity) {
            ActivityBucket.TODAY -> 2.0
            ActivityBucket.THIS_WEEK -> 1.0
            ActivityBucket.THIS_MONTH -> 0.0
            ActivityBucket.EARLIER -> -3.0
        }
        return b
    }

    private fun diversityPenalty(p: Profile, picked: List<RankedMatch>): Double {
        val sameProfession = picked.count { it.profile.profession.equals(p.profession, ignoreCase = true) }
        val sameCity = picked.count { it.profile.location.city.equals(p.location.city, ignoreCase = true) }
        return sameProfession * 3.0 + sameCity * 1.5
    }

    companion object {
        const val MIN_SCORE = 40
    }
}
