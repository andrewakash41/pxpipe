package com.vachan.app.core

import com.vachan.app.core.model.Diet
import com.vachan.app.core.model.EducationLevel
import com.vachan.app.core.model.FamilyType
import com.vachan.app.core.model.Gender
import com.vachan.app.core.model.Location
import com.vachan.app.core.model.PartnerPreferences
import com.vachan.app.core.model.Profile
import com.vachan.app.core.model.Religion
import com.vachan.app.core.model.ValueAnswer
import com.vachan.app.core.model.ValueQuestion
import com.vachan.app.core.model.Verification

object TestProfiles {
    val pune = Location("Pune", "Maharashtra", lat = 18.52, lng = 73.86)
    val mumbai = Location("Mumbai", "Maharashtra", lat = 19.08, lng = 72.88)
    val chennai = Location("Chennai", "Tamil Nadu", lat = 13.08, lng = 80.27)
    val london = Location("London", "England", country = "United Kingdom", lat = 51.51, lng = -0.13)

    fun values(vararg answers: Pair<ValueQuestion, Int>, importance: Int = 2) =
        answers.associate { (q, a) -> q to ValueAnswer(a, importance) }

    val sharedValues = values(
        ValueQuestion.LIVING_ARRANGEMENT to 3,
        ValueQuestion.CAREERS to 4,
        ValueQuestion.CHILDREN to 1,
        ValueQuestion.FINANCES to 2,
        ValueQuestion.HOUSEHOLD to 4,
        ValueQuestion.PARENTS_ROLE to 2,
    )

    fun woman(
        id: String = "w1",
        age: Int = 28,
        location: Location = pune,
        prefs: PartnerPreferences = PartnerPreferences(minAge = 28, maxAge = 34),
        diet: Diet = Diet.VEGETARIAN,
        interests: Set<String> = setOf("Travel", "Music", "Cooking", "Reading"),
        values: Map<ValueQuestion, ValueAnswer> = sharedValues,
        verification: Verification = Verification(phone = true, selfie = true),
    ) = Profile(
        id = id, firstName = "Ananya", lastInitial = "K", gender = Gender.FEMALE, age = age, heightCm = 163,
        location = location, religion = Religion.HINDU, motherTongue = "Marathi", education = EducationLevel.MASTERS,
        profession = "Software engineer", diet = diet, familyType = FamilyType.NUCLEAR, interests = interests,
        languages = setOf("English", "Hindi"), values = values, verification = verification, preferences = prefs,
    )

    fun man(
        id: String = "m1",
        age: Int = 30,
        location: Location = pune,
        prefs: PartnerPreferences = PartnerPreferences(minAge = 25, maxAge = 30),
        diet: Diet = Diet.VEGETARIAN,
        interests: Set<String> = setOf("Travel", "Music", "Cooking", "Cricket"),
        values: Map<ValueQuestion, ValueAnswer> = sharedValues,
        profession: String = "Doctor",
        verification: Verification = Verification(phone = true, governmentId = true),
    ) = Profile(
        id = id, firstName = "Rohan", lastInitial = "D", gender = Gender.MALE, age = age, heightCm = 178,
        location = location, religion = Religion.HINDU, motherTongue = "Marathi", education = EducationLevel.PROFESSIONAL,
        profession = profession, diet = diet, familyType = FamilyType.NUCLEAR, interests = interests,
        languages = setOf("English", "Hindi"), values = values, verification = verification, preferences = prefs,
    )
}
