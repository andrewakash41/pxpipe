package com.vachan.app.core.matching

import com.vachan.app.core.TestProfiles
import com.vachan.app.core.TestProfiles.man
import com.vachan.app.core.TestProfiles.woman
import com.vachan.app.core.model.ActivityBucket
import com.vachan.app.core.model.Diet
import com.vachan.app.core.model.EducationLevel
import com.vachan.app.core.model.MaritalStatus
import com.vachan.app.core.model.Religion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryParserTest {
    @Test
    fun `parses a typical natural-language query`() {
        val f = QueryParser.parse("Vegetarian doctor in Pune or Mumbai, 27-31, open to moving abroad")
        assertEquals(27, f.minAge)
        assertEquals(31, f.maxAge)
        assertEquals(setOf("Pune", "Mumbai"), f.cities)
        assertEquals(setOf(Diet.VEGETARIAN), f.diets)
        assertEquals(setOf("doctor"), f.professions)
        assertEquals(true, f.openToRelocate)
        assertNull("'moving abroad' is about relocating, not living abroad", f.livesAbroad)
    }

    @Test
    fun `non-veg is not mistaken for veg`() {
        assertEquals(setOf(Diet.NON_VEGETARIAN), QueryParser.parse("non-veg engineer").diets)
    }

    @Test
    fun `parses religion, language, education, marital status and NRI`() {
        val f = QueryParser.parse("Tamil Christian MBA under 33 divorced NRI verified")
        assertEquals(setOf(Religion.CHRISTIAN), f.religions)
        assertEquals(setOf("Tamil"), f.motherTongues)
        assertEquals(EducationLevel.MASTERS, f.minEducation)
        assertEquals(33, f.maxAge)
        assertEquals(setOf(MaritalStatus.DIVORCED), f.maritalStatuses)
        assertEquals(true, f.livesAbroad)
        assertTrue(f.verifiedOnly)
    }

    @Test
    fun `bangalore alias maps to Bengaluru`() {
        assertEquals(setOf("Bengaluru"), QueryParser.parse("someone in bangalore").cities)
    }

    @Test
    fun `filters match profiles`() {
        val f = QueryParser.parse("doctor in Pune 28 to 32")
        assertTrue(QueryParser.matches(man(), f))
        assertFalse(QueryParser.matches(man(profession = "Architect"), f))
        assertFalse(QueryParser.matches(man(location = TestProfiles.london), f))
    }
}

class RecommenderTest {
    private val engine = CompatibilityEngine()
    private val recommender = Recommender(engine)

    @Test
    fun `daily picks are eligible, compatible, deduplicated and capped`() {
        val viewer = woman()
        val candidates = (1..20).map { i ->
            man(id = "m$i", age = 28 + (i % 6), profession = if (i % 2 == 0) "Doctor" else "Architect")
        } + woman(id = "w9")
        val picks = recommender.dailyPicks(viewer, candidates, excludeIds = setOf("m1"), blockedIds = setOf("m2"), count = 8)
        assertEquals(8, picks.size)
        assertTrue(picks.none { it.profile.id in setOf("m1", "m2", "w9") })
        assertTrue(picks.all { it.compatibility.score >= Recommender.MIN_SCORE })
        assertEquals(picks.size, picks.map { it.profile.id }.toSet().size)
    }

    @Test
    fun `diversification mixes professions`() {
        val viewer = woman()
        val candidates = (1..10).map { man(id = "d$it", profession = "Doctor") } +
            (1..2).map { man(id = "a$it", profession = "Architect") }
        val picks = recommender.dailyPicks(viewer, candidates, emptySet(), emptySet(), count = 4)
        assertTrue(picks.any { it.profile.profession == "Architect" })
    }

    @Test
    fun `active verified members rank above dormant ones with equal compatibility`() {
        val viewer = woman()
        val active = man(id = "active").copy(activity = ActivityBucket.TODAY)
        val dormant = man(id = "dormant").copy(activity = ActivityBucket.EARLIER)
        val ranked = recommender.rankSearchResults(viewer, listOf(dormant, active), emptySet())
        assertEquals("active", ranked.first().profile.id)
    }
}

class PreferenceLearnerTest {
    @Test
    fun `interest in high-interest-overlap candidates raises that weight and explains it`() {
        val learner = PreferenceLearner()
        var w = FactorWeights()
        val factors = listOf(FactorScore(Factor.INTERESTS, 1.0, ""), FactorScore(Factor.LOCATION, 0.6, ""))
        repeat(10) { w = learner.update(w, factors, PreferenceLearner.Feedback.INTEREST_SENT) }
        assertTrue(w.of(Factor.INTERESTS) > Factor.INTERESTS.defaultWeight)
        assertEquals(Factor.LOCATION.defaultWeight, w.of(Factor.LOCATION), 1e-9)
        assertNotNull(learner.explain(w))
        // Round-trips through storage.
        assertEquals(w.of(Factor.INTERESTS), FactorWeights.fromStored(w.toStored()).of(Factor.INTERESTS), 1e-9)
    }

    @Test
    fun `weights stay within bounds`() {
        val learner = PreferenceLearner(learningRate = 1.0)
        var w = FactorWeights()
        repeat(100) { w = learner.update(w, listOf(FactorScore(Factor.VALUES, 0.0, "")), PreferenceLearner.Feedback.INTEREST_SENT) }
        assertEquals(Factor.VALUES.defaultWeight * FactorWeights.MIN_MULTIPLIER, w.of(Factor.VALUES), 1e-9)
    }
}
