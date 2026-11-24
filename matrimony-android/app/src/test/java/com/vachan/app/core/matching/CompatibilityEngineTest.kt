package com.vachan.app.core.matching

import com.vachan.app.core.TestProfiles
import com.vachan.app.core.TestProfiles.man
import com.vachan.app.core.TestProfiles.woman
import com.vachan.app.core.model.Diet
import com.vachan.app.core.model.Gender
import com.vachan.app.core.model.PartnerPreferences
import com.vachan.app.core.model.PreferenceKey
import com.vachan.app.core.model.ProfileVisibility
import com.vachan.app.core.model.Religion
import com.vachan.app.core.model.ValueAnswer
import com.vachan.app.core.model.ValueQuestion
import com.vachan.app.core.model.Verification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatibilityEngineTest {

    private val engine = CompatibilityEngine()

    @Test
    fun `well matched pair scores highly and symmetrically`() {
        val a = woman()
        val b = man()
        val ab = engine.score(a, b)
        val ba = engine.score(b, a)
        assertTrue("expected strong match, got ${ab.score}", ab.score >= 80)
        assertEquals("two-way score must be symmetric", ab.score, ba.score)
        assertEquals(listOf("Cooking", "Music", "Travel"), ab.sharedInterests)
    }

    @Test
    fun `reciprocity penalises one-sided fit`() {
        val a = woman(age = 28)
        // He fits her, but she is outside his preferred age band by 6 years.
        val oneSided = man(prefs = PartnerPreferences(minAge = 20, maxAge = 22))
        val mutual = man()
        val oneSidedResult = engine.score(a, oneSided)
        val mutualResult = engine.score(a, mutual)
        assertTrue(oneSidedResult.score < mutualResult.score - 10)
        assertTrue(oneSidedResult.youFitThem < oneSidedResult.theyFitYou)
        assertTrue(oneSidedResult.frictions.any { it.startsWith("Outside their preference") })
    }

    @Test
    fun `must-have preferences act as hard filters in both directions`() {
        val a = woman(prefs = PartnerPreferences(minAge = 28, maxAge = 34, religions = setOf(Religion.SIKH), mustHaves = setOf(PreferenceKey.RELIGION)))
        val b = man()
        assertFalse(engine.isEligible(a, b))
        assertFalse(engine.isEligible(b, a))

        val soft = woman(prefs = PartnerPreferences(minAge = 28, maxAge = 34, religions = setOf(Religion.SIKH)))
        assertTrue(engine.isEligible(soft, b))
    }

    @Test
    fun `policy filters respect gender, visibility, verification and blocks`() {
        val a = woman()
        assertFalse(engine.isEligible(a, woman(id = "w2")))
        assertFalse(engine.isEligible(a, man().copy(visibility = ProfileVisibility.HIDDEN)))
        val verifiedOnly = man().copy(visibility = ProfileVisibility.VERIFIED_ONLY)
        assertTrue(engine.isEligible(a, verifiedOnly))
        assertFalse(engine.isEligible(a.copy(verification = Verification(phone = true)), verifiedOnly))
        assertFalse(engine.isEligible(a, man(), blockedIds = setOf("m1")))
        assertFalse(engine.isEligible(a, a))
    }

    @Test
    fun `strongly held opposing values are surfaced as frictions`() {
        val a = woman(values = mapOf(ValueQuestion.CHILDREN to ValueAnswer(0, 3), ValueQuestion.CAREERS to ValueAnswer(4, 2), ValueQuestion.FAITH to ValueAnswer(2, 1)))
        val b = man(values = mapOf(ValueQuestion.CHILDREN to ValueAnswer(4, 3), ValueQuestion.CAREERS to ValueAnswer(4, 2), ValueQuestion.FAITH to ValueAnswer(2, 1)))
        val r = engine.score(a, b)
        assertTrue(r.frictions.any { it.contains("children") })
        assertTrue(r.highlights.any { it.contains("careers") })
    }

    @Test
    fun `diet compatibility treats the vegetarian family as close`() {
        assertEquals(1.0, CompatibilityEngine.dietCompatibility(Diet.VEGETARIAN, Diet.VEGETARIAN), 0.0)
        assertTrue(CompatibilityEngine.dietCompatibility(Diet.VEGETARIAN, Diet.JAIN) > 0.8)
        assertTrue(CompatibilityEngine.dietCompatibility(Diet.JAIN, Diet.NON_VEGETARIAN) < 0.3)
    }

    @Test
    fun `distance preference softens when someone is open to relocating`() {
        val prefs = PartnerPreferences(minAge = 28, maxAge = 34, maxDistanceKm = 100)
        val a = woman(prefs = prefs)
        val far = man(location = TestProfiles.chennai)
        val farButRelocates = far.copy(openToRelocate = true)
        assertTrue(engine.preferenceFit(a, farButRelocates).score > engine.preferenceFit(a, far).score)
    }

    @Test
    fun `learned weights change ranking within bounds`() {
        val a = woman()
        val b = man(interests = emptySet())
        val boosted = engine.withWeights(FactorWeights(mapOf(Factor.VALUES to 5.0)))
        // Multipliers are clamped, so even absurd stored values stay sane.
        assertEquals(Factor.VALUES.defaultWeight * FactorWeights.MAX_MULTIPLIER, FactorWeights(mapOf(Factor.VALUES to 5.0)).of(Factor.VALUES), 1e-9)
        assertTrue(boosted.score(a, b).score >= engine.score(a, b).score)
    }

    @Test
    fun `seeking is derived from gender`() {
        assertEquals(Gender.MALE, woman().seeking)
        assertEquals(Gender.FEMALE, man().seeking)
    }
}
