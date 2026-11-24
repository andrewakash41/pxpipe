package com.vachan.app.core.matching

import com.vachan.app.core.model.Diet
import com.vachan.app.core.model.EducationLevel
import com.vachan.app.core.model.MaritalStatus
import com.vachan.app.core.model.Profile
import com.vachan.app.core.model.Religion
import com.vachan.app.core.model.SearchFilters

/**
 * Rule-based natural-language search parser.
 *
 * With AI assistance on, the server asks a language model to turn the query into the same
 * [SearchFilters] structure (validated against a schema). This parser is the offline/no-consent
 * fallback, and what Demo mode uses. Either way, the user sees and can edit the parsed filters as
 * chips. Search execution is always deterministic.
 */
object QueryParser {

    val knownCities = listOf(
        "Mumbai", "Delhi", "Bengaluru", "Bangalore", "Hyderabad", "Chennai", "Kolkata", "Pune", "Ahmedabad",
        "Jaipur", "Lucknow", "Kochi", "Indore", "Chandigarh", "Coimbatore", "Nagpur", "Bhopal", "Surat",
        "Noida", "Gurugram", "Gurgaon", "Visakhapatnam", "Mysuru", "Thiruvananthapuram", "Patna", "Vadodara",
        "Dubai", "London", "Singapore", "Toronto", "New York", "San Francisco", "Sydney", "Melbourne",
    )

    private val cityAliases = mapOf("bangalore" to "Bengaluru", "gurgaon" to "Gurugram", "bombay" to "Mumbai")

    val knownLanguages = listOf(
        "Hindi", "Marathi", "Tamil", "Telugu", "Kannada", "Malayalam", "Bengali", "Gujarati", "Punjabi",
        "Odia", "Urdu", "Konkani", "Sindhi", "Assamese", "English",
    )

    private val professionWords = mapOf(
        "doctor" to "doctor", "physician" to "doctor", "mbbs" to "doctor", "surgeon" to "doctor",
        "engineer" to "engineer", "software" to "software", "developer" to "software", "techie" to "software",
        "ca" to "chartered accountant", "chartered accountant" to "chartered accountant",
        "lawyer" to "lawyer", "advocate" to "lawyer", "teacher" to "teacher", "professor" to "professor",
        "architect" to "architect", "designer" to "designer", "banker" to "banker", "consultant" to "consultant",
        "civil servant" to "civil servant", "ias" to "civil servant", "government job" to "government",
        "govt job" to "government", "entrepreneur" to "entrepreneur", "business" to "business",
        "pilot" to "pilot", "nurse" to "nurse", "pharmacist" to "pharmacist", "scientist" to "scientist",
        "data scientist" to "data scientist", "product manager" to "product manager", "manager" to "manager",
    )

    fun parse(query: String): SearchFilters {
        val q = " " + query.lowercase().replace(Regex("""[,;]"""), " ") + " "
        var f = SearchFilters()

        // Age: "27-31", "27 to 31", "between 27 and 31", "under 30", "below 30", "above 28", "28+"
        Regex("""\b(\d{2})\s*(?:-|to|–|and)\s*(\d{2})\b""").find(q)?.let { m ->
            val (a, b) = m.destructured
            val lo = a.toInt()
            val hi = b.toInt()
            if (lo in 18..80 && hi in 18..80 && lo <= hi) f = f.copy(minAge = lo, maxAge = hi)
        }
        Regex("""\b(?:under|below|less than|younger than)\s*(\d{2})\b""").find(q)?.let {
            f = f.copy(maxAge = it.groupValues[1].toInt())
        }
        Regex("""\b(?:above|over|older than|more than)\s*(\d{2})\b(?!\s*(?:cm|lakh|lpa|l\b))""").find(q)?.let {
            f = f.copy(minAge = it.groupValues[1].toInt())
        }
        Regex("""\b(\d{2})\s*\+""").find(q)?.let { f = f.copy(minAge = it.groupValues[1].toInt()) }

        // Height: "taller than 5'8", "above 170 cm"
        Regex("""\b(\d)\s*(?:'|ft|feet)\s*(\d{1,2})?""").find(q)?.let { m ->
            val feet = m.groupValues[1].toInt()
            val inches = m.groupValues[2].toIntOrNull() ?: 0
            if (feet in 4..7) f = f.copy(minHeightCm = ((feet * 12 + inches) * 2.54).toInt())
        }
        Regex("""\b(1[4-9]\d)\s*cm\b""").find(q)?.let { f = f.copy(minHeightCm = it.groupValues[1].toInt()) }

        // Cities
        val cities = knownCities.filter { city -> q.contains(" ${city.lowercase()} ") || q.contains(" ${city.lowercase()}") }
            .map { cityAliases[it.lowercase()] ?: it }.toSet()
        if (cities.isNotEmpty()) f = f.copy(cities = cities)

        // Religion
        val religions = buildSet {
            if (has(q, "hindu")) add(Religion.HINDU)
            if (has(q, "muslim") || has(q, "islam")) add(Religion.MUSLIM)
            if (has(q, "christian")) add(Religion.CHRISTIAN)
            if (has(q, "sikh")) add(Religion.SIKH)
            if (has(q, "jain") && !q.contains("jain food")) add(Religion.JAIN)
            if (has(q, "buddhist")) add(Religion.BUDDHIST)
            if (has(q, "parsi") || has(q, "zoroastrian")) add(Religion.PARSI)
        }
        if (religions.isNotEmpty()) f = f.copy(religions = religions)

        // Mother tongue / language
        val tongues = knownLanguages.filter { has(q, it.lowercase()) && it != "English" }.toSet()
        if (tongues.isNotEmpty()) f = f.copy(motherTongues = tongues)

        // Diet
        val diets = buildSet {
            if (has(q, "non veg") || has(q, "non-veg") || has(q, "nonveg") || has(q, "non vegetarian") || has(q, "non-vegetarian")) {
                add(Diet.NON_VEGETARIAN)
            } else if (has(q, "veg") || has(q, "vegetarian") || has(q, "pure veg")) {
                add(Diet.VEGETARIAN)
            }
            if (has(q, "vegan")) add(Diet.VEGAN)
            if (has(q, "eggetarian")) add(Diet.EGGETARIAN)
        }
        if (diets.isNotEmpty()) f = f.copy(diets = diets)

        // Professions (longest phrases first)
        val professions = professionWords.entries.sortedByDescending { it.key.length }
            .filter { has(q, it.key) }.map { it.value }.toSet()
        if (professions.isNotEmpty()) f = f.copy(professions = professions)

        // Education
        when {
            has(q, "phd") || has(q, "doctorate") -> f = f.copy(minEducation = EducationLevel.DOCTORATE)
            has(q, "postgraduate") || has(q, "post graduate") || has(q, "masters") || has(q, "mba") || has(q, "ms") ->
                f = f.copy(minEducation = EducationLevel.MASTERS)
            has(q, "graduate") || has(q, "degree") -> f = f.copy(minEducation = EducationLevel.BACHELORS)
        }

        // Marital status
        val marital = buildSet {
            if (has(q, "never married") || has(q, "unmarried") || has(q, "first marriage")) add(MaritalStatus.NEVER_MARRIED)
            if (has(q, "divorced") || has(q, "divorcee")) add(MaritalStatus.DIVORCED)
            if (has(q, "widow") || has(q, "widowed") || has(q, "widower")) add(MaritalStatus.WIDOWED)
            if (has(q, "second marriage") || has(q, "remarriage")) {
                add(MaritalStatus.DIVORCED); add(MaritalStatus.WIDOWED); add(MaritalStatus.ANNULLED)
            }
        }
        if (marital.isNotEmpty()) f = f.copy(maritalStatuses = marital)

        val relocation = Regex("""\b(relocat\w*|(move|moving|settle|settling|shift|shifting) abroad|open to (moving|move|shifting)( abroad| overseas)?)\b""")
        if (relocation.containsMatchIn(q)) f = f.copy(openToRelocate = true)
        val withoutRelocation = relocation.replace(q, " ")
        if (has(withoutRelocation, "nri") || has(withoutRelocation, "abroad") || has(withoutRelocation, "overseas")) {
            f = f.copy(livesAbroad = true)
        }
        if (has(q, "in india") || has(q, "india based")) f = f.copy(livesAbroad = false)
        if (has(q, "verified")) f = f.copy(verifiedOnly = true)

        return f
    }

    fun matches(p: Profile, f: SearchFilters): Boolean {
        if (f.minAge != null && p.age < f.minAge) return false
        if (f.maxAge != null && p.age > f.maxAge) return false
        if (f.cities.isNotEmpty() && f.cities.none { it.equals(p.location.city, ignoreCase = true) }) return false
        if (f.religions.isNotEmpty() && p.religion !in f.religions) return false
        if (f.motherTongues.isNotEmpty() && f.motherTongues.none { it.equals(p.motherTongue, ignoreCase = true) }) return false
        if (f.diets.isNotEmpty() && p.diet !in f.diets) return false
        if (f.professions.isNotEmpty() && f.professions.none { p.profession.contains(it, ignoreCase = true) }) return false
        if (f.minEducation != null && p.education.rank < f.minEducation.rank) return false
        if (f.maritalStatuses.isNotEmpty() && p.maritalStatus !in f.maritalStatuses) return false
        if (f.openToRelocate == true && !p.openToRelocate) return false
        if (f.livesAbroad == true && !p.isNri) return false
        if (f.livesAbroad == false && p.isNri) return false
        if (f.verifiedOnly && !p.verification.isVerified) return false
        if (f.minHeightCm != null && p.heightCm < f.minHeightCm) return false
        if (f.keywords.isNotEmpty()) {
            val hay = (p.about + " " + p.interests.joinToString(" ") + " " + p.profession).lowercase()
            if (f.keywords.none { hay.contains(it.lowercase()) }) return false
        }
        return true
    }

    private fun has(q: String, word: String): Boolean =
        Regex("""(?<![a-z])${Regex.escape(word)}(?![a-z])""").containsMatchIn(q)
}
