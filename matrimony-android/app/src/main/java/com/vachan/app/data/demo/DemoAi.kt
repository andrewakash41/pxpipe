package com.vachan.app.data.demo

import com.vachan.app.core.matching.CompatibilityResult
import com.vachan.app.core.matching.Factor
import com.vachan.app.core.matching.QueryParser
import com.vachan.app.core.model.MatchInsight
import com.vachan.app.core.model.Profile
import com.vachan.app.core.model.SearchFilters
import com.vachan.app.core.model.ValueQuestion
import com.vachan.app.core.safety.PiiDetector
import com.vachan.app.data.AssistantTurn

/**
 * Offline, template-based stand-in for the server AI gateway, used in Demo mode and as the
 * fallback whenever AI is unavailable or the user hasn't consented. It only uses the structured
 * compatibility factors, never raw private data.
 */
object DemoAi {

    fun insight(me: Profile, other: Profile, c: CompatibilityResult): MatchInsight {
        val pronoun = if (other.gender.name == "FEMALE") "She" else "He"
        val strongest = c.factors.filter { it.score >= 0.75 }.take(2).map { it.factor }
        val opener = when {
            c.score >= 85 -> "You and ${other.firstName} line up on the things that tend to matter most over a lifetime."
            c.score >= 72 -> "There's a lot of common ground between you and ${other.firstName}."
            c.score >= 60 -> "You and ${other.firstName} share some important foundations, with a few things to explore."
            else -> "You and ${other.firstName} are different in several ways — which can work if you talk openly."
        }
        val because = buildList {
            if (Factor.VALUES in strongest) add("your answers on family, careers and money are closely aligned")
            if (Factor.INTERESTS in strongest && c.sharedInterests.isNotEmpty()) add("you both enjoy ${c.sharedInterests.take(2).joinToString(" and ").lowercase()}")
            if (Factor.LIFESTYLE in strongest) add("your everyday habits fit well")
            if (Factor.LOCATION in strongest) add("distance won't be a hurdle")
            if (Factor.LANGUAGE in strongest && me.motherTongue.equals(other.motherTongue, true)) add("you share ${other.motherTongue} as a mother tongue")
        }
        val middle = if (because.isNotEmpty()) " In particular, ${because.joinToString(", ")}." else ""
        val fitLine = " $pronoun fits ${c.theyFitYou}% of what you're looking for, and you fit ${c.youFitThem}% of what ${pronoun.lowercase()} wants."
        val talking = buildList {
            c.frictions.filter { it.startsWith("Different views") }.take(2).forEach { add(it.substringBefore(" —") + " — share what matters to you and why.") }
            if (c.sharedInterests.isNotEmpty()) add("Plan something around ${c.sharedInterests.first().lowercase()} — shared activities make first meetings easier.")
            val topic = ValueQuestion.entries.firstOrNull { me.values[it] != null && other.values[it] != null && me.values[it]!!.importance >= 2 }
            topic?.let { add("Ask how they imagine ${it.topic} after marriage.") }
        }.ifEmpty { listOf("Ask what a good weekend looks like for them.", "Talk about how involved your families would like to be.") }
        return MatchInsight(summary = opener + middle + fitLine, talkingPoints = talking.take(3), generatedByAi = false)
    }

    fun icebreakers(me: Profile, other: Profile, c: CompatibilityResult): List<String> {
        val ideas = mutableListOf<String>()
        c.sharedInterests.take(2).forEach { interest ->
            ideas += when (interest) {
                "Trekking" -> "I see you trek too! What's the most memorable trail you've done?"
                "Cooking" -> "Fellow cook here — what's the one dish you'd make to impress someone?"
                "Travel" -> "If you could take a trip next month, where would you go?"
                "Reading" -> "What's a book you keep recommending to everyone?"
                "Music", "Classical music" -> "What's been on repeat for you lately?"
                "Cricket" -> "Important question first: Test cricket or T20?"
                "Coffee" -> "Filter coffee, cold brew, or chai loyalist?"
                "Running" -> "Are you training for any runs this season?"
                "Photography" -> "What do you love photographing most?"
                else -> "We both enjoy ${interest.lowercase()} — how did you get into it?"
            }
        }
        if (other.location.city != me.location.city) ideas += "What do you love most about life in ${other.location.city}?"
        ideas += "What does a perfect Sunday look like for you?"
        ideas += "Your profile says you're a ${other.profession.lowercase()} — what's the best part of your work?"
        return ideas.distinct().take(3)
    }

    fun bio(p: Profile, tone: String): String {
        val interests = p.interests.take(3).map { it.lowercase() }
        val family = when (p.familyType.name) {
            "JOINT" -> "I'm close to my family and enjoy the warmth of a joint household."
            "NUCLEAR" -> "I value independence and would like to build our own home together."
            else -> "I'm flexible about living arrangements and close to my family."
        }
        val opener = when (tone) {
            "Warm" -> "Hi! I'm ${p.firstName}, a ${p.profession.lowercase()} based in ${p.location.city}."
            "Fun" -> "${p.firstName} here — ${p.profession.lowercase()} by day, ${interests.firstOrNull() ?: "explorer"} enthusiast by weekend."
            else -> "I'm ${p.firstName}, a ${p.profession.lowercase()} in ${p.location.city} with a ${p.education.label.lowercase()} in ${p.educationField.ifBlank { "my field" }}."
        }
        val likes = if (interests.isNotEmpty()) " I enjoy ${interests.joinToString(", ")}." else ""
        val looking = " I'm looking for a partner who is kind, honest and sees marriage as a partnership."
        return PiiDetector.redact("$opener$likes $family$looking")
    }

    fun parseSearch(query: String): SearchFilters = QueryParser.parse(query)

    fun kindDecline(name: String): String =
        "Hi $name, thank you for your interest and for taking the time to reach out. I don't feel we're the right match, " +
            "but I truly wish you the very best in finding your person."

    fun translate(text: String, target: String): String =
        "[$target translation needs AI to be enabled] $text"

    fun assistant(history: List<AssistantTurn>, me: Profile?, learned: String?): String {
        val q = history.lastOrNull { it.fromUser }?.text?.lowercase().orEmpty()
        return when {
            listOf("scam", "money", "fraud", "safe", "safety").any { it in q } ->
                "Some quick safety rules: never send money, gift cards or OTPs to a match; keep chats here until you've met; " +
                    "meet first in a public place and use \"Meet safely\" to share your plan with a trusted contact. " +
                    "If anyone asks for money, tap Report — we review every report within 24 hours."
            listOf("more matches", "no matches", "few matches", "better matches").any { it in q } -> buildString {
                append("A few things that usually help: ")
                if (me != null && me.preferences.mustHaves.size > 2) append("you have ${me.preferences.mustHaves.size} must-haves — try turning one into a soft preference. ")
                if (me != null && me.preferences.maxAge - me.preferences.minAge < 4) append("Your age range is narrow; widening it by 2 years often doubles matches. ")
                if (me != null && me.completeness() < 80) append("Completing your profile (it's at ${me.completeness()}%) helps others say yes. ")
                append("And verified profiles get noticeably more responses.")
            }
            listOf("bio", "about me", "profile").any { it in q } ->
                "Open your profile and tap \"Help me write\" — I'll draft a bio from your answers that you can edit. " +
                    "Good bios mention what you do, what you enjoy, and what kind of partnership you want. Avoid contact details; they're hidden automatically."
            listOf("learn", "why", "picks", "recommend").any { it in q } ->
                (learned ?: "Your Daily Picks are ranked by two-way compatibility: how well each person fits what you want, how well you fit what they want, plus shared values, lifestyle and interests.") +
                    " You can always see the full breakdown on any profile."
            listOf("family", "parents", "mother", "father").any { it in q } ->
                "Family Mode lets you invite a parent or sibling to help. You choose exactly what they can do — view matches, shortlist or suggest — and they can never read your chats or respond on your behalf."
            listOf("delete", "privacy", "data").any { it in q } ->
                "You're in control: Privacy Center lets you hide your profile, choose who sees your photos, download all your data, or delete your account. Deletion has a 30-day undo window."
            else ->
                "I can help you refine your preferences, improve your profile, understand a match, or stay safe while chatting. What would you like to do?"
        }
    }
}
