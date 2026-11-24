package com.vachan.app.core.matching

import kotlin.math.abs

/**
 * Learns, on the device, which compatibility factors matter more or less to this person, from
 * what they actually do. The result is a set of bounded weight multipliers (0.5x–2x) — never a
 * hidden model — and the app explains the biggest change so the user can accept or reset it.
 */
class PreferenceLearner(private val learningRate: Double = 0.06) {

    enum class Feedback(val direction: Double) {
        INTEREST_SENT(1.0),
        SHORTLISTED(0.5),
        VIEWED_LONG(0.2),
        PASSED(-0.6),
    }

    fun update(weights: FactorWeights, factors: List<FactorScore>, feedback: Feedback): FactorWeights {
        val updated = weights.multipliers.toMutableMap()
        for (f in factors) {
            // Centre each factor around "average" (0.6). Positive feedback on a candidate who scored high
            // on a factor increases that factor's weight; positive feedback despite a low score lowers it.
            // Passing on a candidate who scored high on a factor suggests it matters less than assumed.
            val centred = f.score - 0.6
            val delta = learningRate * feedback.direction * centred
            val current = updated[f.factor] ?: 1.0
            updated[f.factor] = (current + delta).coerceIn(FactorWeights.MIN_MULTIPLIER, FactorWeights.MAX_MULTIPLIER)
        }
        return FactorWeights(updated)
    }

    /** A plain-language description of the largest learned change, or null if nothing notable yet. */
    fun explain(weights: FactorWeights): String? {
        val (factor, m) = weights.multipliers.maxByOrNull { abs(it.value - 1.0) } ?: return null
        if (abs(m - 1.0) < 0.15) return null
        return if (m > 1.0) {
            "You seem to care about ${factor.label.lowercase()} more than most. We're giving it more weight in your Daily Picks."
        } else {
            "${factor.label} seems to matter less to you than we assumed. We're giving it less weight."
        }
    }
}
