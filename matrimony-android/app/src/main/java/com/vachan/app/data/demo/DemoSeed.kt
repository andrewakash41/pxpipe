package com.vachan.app.data.demo

import com.vachan.app.core.model.ActivityBucket
import com.vachan.app.core.model.Diet
import com.vachan.app.core.model.EducationLevel
import com.vachan.app.core.model.FamilyType
import com.vachan.app.core.model.Gender
import com.vachan.app.core.model.Habit
import com.vachan.app.core.model.IncomeBand
import com.vachan.app.core.model.Location
import com.vachan.app.core.model.ManagedBy
import com.vachan.app.core.model.MaritalStatus
import com.vachan.app.core.model.PartnerPreferences
import com.vachan.app.core.model.Photo
import com.vachan.app.core.model.PhotoVisibility
import com.vachan.app.core.model.PreferenceKey
import com.vachan.app.core.model.Profile
import com.vachan.app.core.model.ProfileVisibility
import com.vachan.app.core.model.Religion
import com.vachan.app.core.model.ValueAnswer
import com.vachan.app.core.model.ValueQuestion
import com.vachan.app.core.model.Verification

/**
 * Fictional people for Demo mode. Names, details and photos (illustrated avatars) are invented;
 * any resemblance to real people is coincidental.
 */
object DemoSeed {

    object Cities {
        val mumbai = Location("Mumbai", "Maharashtra", lat = 19.08, lng = 72.88)
        val pune = Location("Pune", "Maharashtra", lat = 18.52, lng = 73.86)
        val bengaluru = Location("Bengaluru", "Karnataka", lat = 12.97, lng = 77.59)
        val hyderabad = Location("Hyderabad", "Telangana", lat = 17.39, lng = 78.49)
        val chennai = Location("Chennai", "Tamil Nadu", lat = 13.08, lng = 80.27)
        val delhi = Location("Delhi", "Delhi", lat = 28.61, lng = 77.21)
        val kolkata = Location("Kolkata", "West Bengal", lat = 22.57, lng = 88.36)
        val ahmedabad = Location("Ahmedabad", "Gujarat", lat = 23.02, lng = 72.57)
        val jaipur = Location("Jaipur", "Rajasthan", lat = 26.91, lng = 75.79)
        val lucknow = Location("Lucknow", "Uttar Pradesh", lat = 26.85, lng = 80.95)
        val kochi = Location("Kochi", "Kerala", lat = 9.93, lng = 76.27)
        val indore = Location("Indore", "Madhya Pradesh", lat = 22.72, lng = 75.86)
        val chandigarh = Location("Chandigarh", "Chandigarh", lat = 30.73, lng = 76.78)
        val coimbatore = Location("Coimbatore", "Tamil Nadu", lat = 11.02, lng = 76.96)
        val gurugram = Location("Gurugram", "Haryana", lat = 28.46, lng = 77.03)
        val london = Location("London", "England", country = "United Kingdom", lat = 51.51, lng = -0.13)
        val dubai = Location("Dubai", "Dubai", country = "UAE", lat = 25.20, lng = 55.27)
        val toronto = Location("Toronto", "Ontario", country = "Canada", lat = 43.65, lng = -79.38)
        val singapore = Location("Singapore", "Singapore", country = "Singapore", lat = 1.35, lng = 103.82)
        val sanFrancisco = Location("San Francisco", "California", country = "USA", lat = 37.77, lng = -122.42)

        val all = listOf(
            mumbai, pune, bengaluru, hyderabad, chennai, delhi, kolkata, ahmedabad, jaipur, lucknow, kochi,
            indore, chandigarh, coimbatore, gurugram, london, dubai, toronto, singapore, sanFrancisco,
        )
    }

    val interestOptions = listOf(
        "Travel", "Music", "Cooking", "Reading", "Fitness", "Yoga", "Cricket", "Movies", "Photography", "Trekking",
        "Dance", "Art", "Gaming", "Volunteering", "Gardening", "Startups", "Theatre", "Badminton", "Running",
        "Classical music", "Board games", "Pets", "Coffee", "Poetry", "Food", "Spirituality", "Football", "Cycling",
    )

    val languageOptions = listOf(
        "Hindi", "English", "Marathi", "Tamil", "Telugu", "Kannada", "Malayalam", "Bengali", "Gujarati", "Punjabi",
        "Urdu", "Odia", "Konkani", "Sindhi", "Assamese",
    )

    /** Values answers in [ValueQuestion] order; importance pattern cycles to vary what people care about. */
    private fun values(vararg answers: Int, importance: IntArray = intArrayOf(2, 2, 3, 1, 2, 1, 2, 2, 1, 1, 2, 1)) =
        ValueQuestion.entries.zip(answers.toList()).mapIndexed { i, (q, a) -> q to ValueAnswer(a, importance[i % importance.size]) }.toMap()

    private var seedCounter = 0

    private fun p(
        id: String,
        first: String,
        initial: String,
        gender: Gender,
        age: Int,
        height: Int,
        city: Location,
        religion: Religion?,
        tongue: String,
        education: EducationLevel,
        field: String,
        profession: String,
        diet: Diet,
        interests: List<String>,
        values: Map<ValueQuestion, ValueAnswer>,
        about: String,
        income: IncomeBand = IncomeBand.UNDISCLOSED,
        marital: MaritalStatus = MaritalStatus.NEVER_MARRIED,
        family: FamilyType = FamilyType.FLEXIBLE,
        managedBy: ManagedBy = ManagedBy.SELF,
        verification: Verification = Verification(phone = true, selfie = true),
        relocate: Boolean = false,
        smoking: Habit = Habit.NEVER,
        drinking: Habit = Habit.NEVER,
        languages: Set<String> = setOf("English", "Hindi"),
        hasChildren: Boolean = false,
        responseRate: Float = 0.8f,
        activity: ActivityBucket = ActivityBucket.THIS_WEEK,
        prefs: PartnerPreferences? = null,
        photoVisibility: PhotoVisibility = PhotoVisibility.ALL_MEMBERS,
        visibility: ProfileVisibility = ProfileVisibility.EVERYONE,
        residency: String? = null,
    ): Profile {
        seedCounter++
        val partnerMin = if (gender == Gender.FEMALE) maxOf(21, age - 1) else maxOf(18, age - 7)
        val partnerMax = if (gender == Gender.FEMALE) age + 7 else age + 2
        return Profile(
            id = id, firstName = first, lastInitial = initial, gender = gender, age = age, heightCm = height,
            location = city, religion = religion, motherTongue = tongue, education = education, educationField = field,
            profession = profession, incomeBand = income, diet = diet, smoking = smoking, drinking = drinking,
            familyType = family, managedBy = managedBy, about = about, interests = interests.toSet(),
            languages = languages + tongue, values = values, openToRelocate = relocate, verification = verification,
            maritalStatus = marital, hasChildren = hasChildren, responseRate = responseRate, activity = activity,
            photos = listOf(Photo("ph_$id", seed = seedCounter * 37)), photoVisibility = photoVisibility,
            visibility = visibility, memberCode = "VCH-${1000 + seedCounter * 17}", residencyStatus = residency,
            preferences = prefs ?: PartnerPreferences(
                minAge = partnerMin,
                maxAge = partnerMax,
                maritalStatuses = if (marital == MaritalStatus.NEVER_MARRIED) setOf(MaritalStatus.NEVER_MARRIED, MaritalStatus.DIVORCED, MaritalStatus.ANNULLED)
                else MaritalStatus.entries.toSet(),
            ),
        )
    }

    val women: List<Profile> = listOf(
        p("w01", "Ananya", "K", Gender.FEMALE, 28, 163, Cities.pune, Religion.HINDU, "Marathi", EducationLevel.MASTERS, "Computer Science",
            "Software engineer", Diet.VEGETARIAN, listOf("Trekking", "Classical music", "Cooking", "Reading"),
            values(3, 4, 1, 3, 2, 2, 4, 2, 4, 1, 4, 1), income = IncomeBand.L20_35,
            about = "I build payment systems by day and learn Hindustani vocals on weekends. Happiest on a Sahyadri trail with a thermos of chai. Looking for a kind, curious partner who sees marriage as a team sport.",
            verification = Verification(phone = true, selfie = true, governmentId = true, workEmail = true), activity = ActivityBucket.TODAY),
        p("w02", "Fatima", "S", Gender.FEMALE, 27, 158, Cities.hyderabad, Religion.MUSLIM, "Urdu", EducationLevel.PROFESSIONAL, "Medicine (MBBS)",
            "Doctor", Diet.NON_VEGETARIAN, listOf("Poetry", "Food", "Volunteering", "Photography"),
            values(1, 4, 1, 2, 1, 1, 3, 2, 2, 1, 3, 1), family = FamilyType.JOINT, managedBy = ManagedBy.PARENT,
            about = "Resident doctor at a government hospital. My parents helped set up this profile, and I'm the one replying. I love Urdu poetry, Old City food walks and weekend health camps.",
            prefs = PartnerPreferences(minAge = 27, maxAge = 34, religions = setOf(Religion.MUSLIM), mustHaves = setOf(PreferenceKey.RELIGION)),
            photoVisibility = PhotoVisibility.AFTER_ACCEPT),
        p("w03", "Priya", "R", Gender.FEMALE, 30, 165, Cities.bengaluru, Religion.HINDU, "Kannada", EducationLevel.MASTERS, "MBA",
            "Product manager", Diet.EGGETARIAN, listOf("Running", "Startups", "Coffee", "Travel"),
            values(3, 4, 2, 4, 3, 3, 4, 3, 4, 2, 4, 2), income = IncomeBand.L35_50, relocate = true, drinking = Habit.OCCASIONALLY,
            about = "PM at a health-tech startup, half-marathoner, and a serious filter-coffee snob. Open to moving for the right person and the right adventure.",
            verification = Verification(phone = true, selfie = true, workEmail = true), activity = ActivityBucket.TODAY),
        p("w04", "Harleen", "G", Gender.FEMALE, 29, 168, Cities.chandigarh, Religion.SIKH, "Punjabi", EducationLevel.BACHELORS, "Architecture",
            "Architect", Diet.VEGETARIAN, listOf("Art", "Dance", "Travel", "Gardening"),
            values(2, 4, 1, 3, 1, 2, 3, 2, 3, 1, 3, 2), family = FamilyType.JOINT,
            about = "I design homes that let light in. Bhangra at weddings, gurdwara on Sundays, and a terrace full of plants. Family is everything to me."),
        p("w05", "Meera", "N", Gender.FEMALE, 31, 160, Cities.kochi, Religion.CHRISTIAN, "Malayalam", EducationLevel.DOCTORATE, "Marine Biology",
            "Scientist", Diet.NON_VEGETARIAN, listOf("Reading", "Photography", "Trekking", "Board games"),
            values(3, 4, 2, 4, 2, 2, 4, 3, 4, 1, 4, 1), relocate = true,
            about = "Marine biologist studying coral reefs. I've spent more time on research boats than I'd like to admit. Looking for someone who's curious about the world and kind to people."),
        p("w06", "Sneha", "P", Gender.FEMALE, 26, 155, Cities.ahmedabad, Religion.JAIN, "Gujarati", EducationLevel.PROFESSIONAL, "Chartered Accountancy",
            "Chartered accountant", Diet.JAIN, listOf("Spirituality", "Music", "Cooking", "Badminton"),
            values(1, 3, 1, 1, 0, 1, 2, 1, 1, 1, 2, 0), family = FamilyType.JOINT, managedBy = ManagedBy.PARENT,
            about = "CA at a family firm. I follow Jain food practices and enjoy simple living, garba nights and badminton. Close to my family and hope to stay in Gujarat.",
            prefs = PartnerPreferences(minAge = 26, maxAge = 31, diets = setOf(Diet.JAIN, Diet.VEGETARIAN), mustHaves = setOf(PreferenceKey.DIET))),
        p("w07", "Divya", "S", Gender.FEMALE, 32, 162, Cities.london, Religion.HINDU, "Tamil", EducationLevel.MASTERS, "Finance",
            "Investment analyst", Diet.VEGETARIAN, listOf("Theatre", "Classical music", "Travel", "Running"),
            values(3, 4, 1, 4, 2, 3, 4, 3, 3, 1, 4, 2), relocate = false, residency = "Permanent resident (UK)",
            about = "Chennai girl in London for 8 years. Carnatic music, West End shows and Sunday sambar. Looking for someone settled abroad or open to moving to the UK.",
            verification = Verification(phone = true, selfie = true, governmentId = true)),
        p("w08", "Aisha", "K", Gender.FEMALE, 34, 166, Cities.mumbai, Religion.MUSLIM, "Hindi", EducationLevel.MASTERS, "Journalism",
            "Journalist", Diet.NON_VEGETARIAN, listOf("Reading", "Movies", "Poetry", "Coffee"),
            values(3, 4, 2, 3, 3, 3, 4, 3, 3, 0, 4, 2), marital = MaritalStatus.DIVORCED,
            about = "Journalist, chai person, and believer in second chances. Divorced two years ago, amicably. Looking for honesty, warmth and good conversation.",
            prefs = PartnerPreferences(minAge = 32, maxAge = 42, maritalStatuses = MaritalStatus.entries.toSet())),
        p("w09", "Lakshmi", "V", Gender.FEMALE, 27, 159, Cities.coimbatore, Religion.HINDU, "Tamil", EducationLevel.BACHELORS, "Textile Engineering",
            "Engineer", Diet.VEGETARIAN, listOf("Dance", "Cooking", "Spirituality", "Gardening"),
            values(1, 3, 0, 1, 1, 1, 2, 1, 1, 1, 2, 0), family = FamilyType.JOINT, languages = setOf("Tamil", "English"),
            about = "Engineer in our family textile business. Bharatanatyam since I was six. I'd love a partner who respects tradition and also lets me run the business my way."),
        p("w10", "Riya", "B", Gender.FEMALE, 29, 161, Cities.kolkata, Religion.HINDU, "Bengali", EducationLevel.MASTERS, "Economics",
            "Professor", Diet.NON_VEGETARIAN, listOf("Reading", "Theatre", "Food", "Music"),
            values(2, 4, 2, 2, 3, 2, 4, 3, 3, 1, 4, 2),
            about = "I teach economics, argue about football, and will take you to the best phuchka stall in south Kolkata. Rabindra sangeet on loop.", languages = setOf("Bengali", "English", "Hindi")),
        p("w11", "Nisha", "M", Gender.FEMALE, 28, 164, Cities.gurugram, Religion.HINDU, "Hindi", EducationLevel.BACHELORS, "Computer Science",
            "Data scientist", Diet.NON_VEGETARIAN, listOf("Gaming", "Fitness", "Movies", "Startups"),
            values(3, 4, 3, 3, 3, 3, 4, 3, 3, 2, 4, 3), drinking = Habit.OCCASIONALLY,
            about = "Data scientist who plays way too much FIFA. Gym five days a week. Looking for a best friend first.", activity = ActivityBucket.TODAY),
        p("w12", "Pooja", "T", Gender.FEMALE, 33, 157, Cities.lucknow, Religion.HINDU, "Hindi", EducationLevel.MASTERS, "Education",
            "Teacher", Diet.VEGETARIAN, listOf("Poetry", "Cooking", "Gardening", "Volunteering"),
            values(2, 3, 1, 2, 1, 2, 3, 2, 2, 1, 3, 1), marital = MaritalStatus.WIDOWED, hasChildren = true,
            about = "Teacher and mother to a cheerful seven-year-old. Lost my husband three years ago. Looking for a gentle, mature partner who would welcome my daughter.",
            prefs = PartnerPreferences(minAge = 32, maxAge = 42, maritalStatuses = MaritalStatus.entries.toSet())),
        p("w13", "Simran", "A", Gender.FEMALE, 30, 167, Cities.toronto, Religion.SIKH, "Punjabi", EducationLevel.MASTERS, "Nursing",
            "Nurse", Diet.NON_VEGETARIAN, listOf("Fitness", "Travel", "Dance", "Food"),
            values(3, 4, 1, 4, 2, 2, 4, 2, 3, 1, 4, 2), residency = "Canadian citizen", relocate = false,
            about = "ICU nurse in Toronto. Grew up in Ludhiana, moved here for my master's. I'd love someone who's settled in Canada or ready to make the move."),
        p("w14", "Kavya", "I", Gender.FEMALE, 27, 162, Cities.hyderabad, Religion.HINDU, "Telugu", EducationLevel.MASTERS, "Design",
            "UX designer", Diet.EGGETARIAN, listOf("Art", "Photography", "Travel", "Coffee"),
            values(3, 4, 2, 3, 2, 2, 4, 3, 3, 1, 4, 2),
            about = "Designer who sketches strangers on the metro (with permission!). Weekend photo walks and biryani debates.", verification = Verification(phone = true, selfie = true, governmentId = true), activity = ActivityBucket.TODAY),
        // Demo scam persona (used only for the Safety Copilot walkthrough).
        p("w99", "Sophia", "W", Gender.FEMALE, 29, 170, Cities.london, Religion.CHRISTIAN, "English", EducationLevel.MASTERS, "Engineering",
            "Offshore engineer", Diet.NON_VEGETARIAN, listOf("Travel", "Music", "Movies"),
            values(2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2), verification = Verification(phone = true), responseRate = 1f,
            about = "Engineer working on an oil rig. I am looking for true love and a serious relationship."),
    )

    val men: List<Profile> = listOf(
        p("m01", "Rohan", "D", Gender.MALE, 30, 178, Cities.pune, Religion.HINDU, "Marathi", EducationLevel.PROFESSIONAL, "Medicine (MD)",
            "Doctor", Diet.VEGETARIAN, listOf("Trekking", "Cricket", "Cooking", "Classical music"),
            values(3, 4, 1, 3, 2, 2, 4, 2, 4, 1, 4, 1), income = IncomeBand.L20_35,
            about = "Paediatrician who still gets excited about a good monsoon trek. I cook a mean pithla-bhakri. Looking for a partner who values honesty and laughs easily.",
            verification = Verification(phone = true, selfie = true, governmentId = true), activity = ActivityBucket.TODAY),
        p("m02", "Arjun", "N", Gender.MALE, 31, 180, Cities.bengaluru, Religion.HINDU, "Tamil", EducationLevel.MASTERS, "Computer Science",
            "Software engineer", Diet.EGGETARIAN, listOf("Running", "Board games", "Travel", "Coffee"),
            values(3, 4, 2, 4, 3, 3, 4, 3, 4, 2, 4, 2), income = IncomeBand.L35_50, relocate = true,
            about = "Backend engineer and amateur board-game designer. I run 10Ks for the free bananas. Equal partnership matters a lot to me.",
            verification = Verification(phone = true, selfie = true, workEmail = true), activity = ActivityBucket.TODAY),
        p("m03", "Imran", "Q", Gender.MALE, 32, 176, Cities.hyderabad, Religion.MUSLIM, "Urdu", EducationLevel.MASTERS, "Business",
            "Entrepreneur", Diet.NON_VEGETARIAN, listOf("Food", "Cricket", "Travel", "Startups"),
            values(1, 3, 1, 2, 1, 1, 3, 2, 2, 1, 3, 2), family = FamilyType.JOINT,
            about = "Run a logistics startup with my brother. Family dinners every Friday are non-negotiable. Looking for someone ambitious and grounded.",
            prefs = PartnerPreferences(minAge = 24, maxAge = 31, religions = setOf(Religion.MUSLIM), mustHaves = setOf(PreferenceKey.RELIGION))),
        p("m04", "Gurpreet", "S", Gender.MALE, 33, 183, Cities.chandigarh, Religion.SIKH, "Punjabi", EducationLevel.BACHELORS, "Mechanical Engineering",
            "Civil servant", Diet.VEGETARIAN, listOf("Fitness", "Music", "Volunteering", "Gardening"),
            values(2, 4, 1, 2, 1, 2, 3, 2, 3, 1, 3, 1), family = FamilyType.JOINT, verification = Verification(phone = true, selfie = true, governmentId = true),
            about = "State civil services officer. Seva at the gurdwara, morning runs, and old Punjabi folk songs. Looking for a life partner who believes in giving back."),
        p("m05", "Thomas", "J", Gender.MALE, 34, 175, Cities.kochi, Religion.CHRISTIAN, "Malayalam", EducationLevel.MASTERS, "Hospitality",
            "Chef", Diet.NON_VEGETARIAN, listOf("Cooking", "Music", "Football", "Travel"),
            values(3, 4, 2, 3, 2, 2, 4, 3, 4, 1, 3, 2), drinking = Habit.OCCASIONALLY,
            about = "Head chef at a seafood place by the backwaters. Will absolutely feed you. Football on weekends, church choir on Sundays."),
        p("m06", "Karan", "M", Gender.MALE, 29, 172, Cities.ahmedabad, Religion.JAIN, "Gujarati", EducationLevel.PROFESSIONAL, "Chartered Accountancy",
            "Chartered accountant", Diet.JAIN, listOf("Spirituality", "Badminton", "Reading", "Cooking"),
            values(1, 3, 1, 1, 0, 1, 2, 1, 1, 1, 2, 0), family = FamilyType.JOINT, managedBy = ManagedBy.PARENT,
            about = "CA running our family practice. Simple living, Paryushan fasts, and badminton at 6 am. Profile managed with my mother's help."),
        p("m07", "Vikram", "R", Gender.MALE, 33, 179, Cities.sanFrancisco, Religion.HINDU, "Telugu", EducationLevel.MASTERS, "Electrical Engineering",
            "Engineering manager", Diet.NON_VEGETARIAN, listOf("Cycling", "Photography", "Travel", "Startups"),
            values(3, 4, 2, 4, 3, 3, 4, 3, 4, 2, 4, 2), residency = "Green card (USA)", income = IncomeBand.ABOVE_1CR, drinking = Habit.OCCASIONALLY,
            about = "Engineering manager in the Bay Area, originally from Vijayawada. Weekend cycling up Mt. Tam and photography. Looking for a partner happy to build a life in the US.",
            verification = Verification(phone = true, selfie = true, governmentId = true)),
        p("m08", "Siddharth", "B", Gender.MALE, 36, 177, Cities.mumbai, Religion.HINDU, "Bengali", EducationLevel.MASTERS, "Film",
            "Film editor", Diet.NON_VEGETARIAN, listOf("Movies", "Theatre", "Music", "Coffee"),
            values(3, 4, 2, 3, 3, 3, 4, 3, 3, 0, 4, 2), marital = MaritalStatus.DIVORCED,
            about = "Film editor. Divorced, no kids, no drama (except on screen). Believe in therapy, long walks and Satyajit Ray.",
            prefs = PartnerPreferences(minAge = 29, maxAge = 38, maritalStatuses = MaritalStatus.entries.toSet())),
        p("m09", "Karthik", "S", Gender.MALE, 29, 174, Cities.chennai, Religion.HINDU, "Tamil", EducationLevel.BACHELORS, "Commerce",
            "Banker", Diet.VEGETARIAN, listOf("Cricket", "Classical music", "Spirituality", "Food"),
            values(1, 3, 0, 1, 1, 1, 2, 1, 1, 1, 2, 0), family = FamilyType.JOINT, languages = setOf("Tamil", "English"),
            about = "Banker by profession, Carnatic violinist by passion. My family is traditional and close-knit, and I'd love a partner who enjoys that."),
        p("m10", "Aditya", "G", Gender.MALE, 30, 181, Cities.delhi, Religion.HINDU, "Hindi", EducationLevel.PROFESSIONAL, "Law (LLB)",
            "Lawyer", Diet.NON_VEGETARIAN, listOf("Reading", "Football", "Food", "Travel"),
            values(3, 4, 2, 3, 3, 3, 4, 3, 3, 2, 4, 2), drinking = Habit.OCCASIONALLY,
            about = "Litigation lawyer who argues for a living and loses every argument at home. Parathas at Chandni Chowk, Premier League at night.", activity = ActivityBucket.TODAY),
        p("m11", "Rahul", "V", Gender.MALE, 35, 173, Cities.indore, Religion.HINDU, "Hindi", EducationLevel.MASTERS, "Education",
            "Professor", Diet.VEGETARIAN, listOf("Poetry", "Gardening", "Reading", "Volunteering"),
            values(2, 3, 1, 2, 1, 2, 3, 2, 2, 1, 3, 1), marital = MaritalStatus.WIDOWED,
            about = "Professor of Hindi literature. Widowed four years ago. Gentle, patient, and fond of early mornings in the garden. Would welcome a partner with children.",
            prefs = PartnerPreferences(minAge = 29, maxAge = 38, maritalStatuses = MaritalStatus.entries.toSet())),
        p("m12", "Neil", "D", Gender.MALE, 31, 178, Cities.dubai, Religion.HINDU, "Konkani", EducationLevel.MASTERS, "MBA",
            "Consultant", Diet.NON_VEGETARIAN, listOf("Fitness", "Travel", "Food", "Football"),
            values(3, 4, 2, 4, 2, 3, 4, 3, 4, 1, 3, 2), residency = "UAE resident (work visa)", relocate = true,
            about = "Consultant in Dubai, originally from Goa. Beach person stuck in a desert. Looking for someone adventurous and family-oriented."),
        p("m13", "Aman", "K", Gender.MALE, 28, 176, Cities.jaipur, Religion.HINDU, "Hindi", EducationLevel.BACHELORS, "Hotel Management",
            "Entrepreneur", Diet.VEGETARIAN, listOf("Photography", "Travel", "Art", "Music"),
            values(2, 4, 1, 3, 2, 2, 3, 2, 4, 1, 3, 2), family = FamilyType.JOINT,
            about = "I run a heritage homestay in the old city. Love showing guests hidden step-wells. Looking for a partner who loves people and stories."),
        p("m14", "Anirudh", "P", Gender.MALE, 32, 182, Cities.bengaluru, Religion.HINDU, "Kannada", EducationLevel.DOCTORATE, "Physics",
            "Scientist", Diet.VEGETARIAN, listOf("Reading", "Trekking", "Board games", "Classical music"),
            values(3, 4, 2, 4, 3, 2, 4, 3, 3, 1, 4, 1), relocate = true, verification = Verification(phone = true, selfie = true, governmentId = true, workEmail = true),
            about = "Researcher at a space-science institute. Weekends in the Western Ghats, weeknights losing at chess online."),
        // Demo scam persona (used only for the Safety Copilot walkthrough).
        p("m99", "Daniel", "W", Gender.MALE, 34, 185, Cities.london, Religion.CHRISTIAN, "English", EducationLevel.MASTERS, "Engineering",
            "Offshore engineer", Diet.NON_VEGETARIAN, listOf("Travel", "Music", "Movies"),
            values(2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2), verification = Verification(phone = true), responseRate = 1f,
            about = "Engineer working on an oil rig. Widower looking for true love and a serious relationship.", marital = MaritalStatus.WIDOWED),
    )

    const val SCAM_PERSONA_WOMAN = "w99"
    const val SCAM_PERSONA_MAN = "m99"

    fun all(): List<Profile> = women + men

    /** Scripted messages for the two demo conversations created after onboarding. */
    val genuineScript = listOf(
        "Hi! Your profile made me smile. Is the trekking photo from Rajmachi?",
        "I've been meaning to do the monsoon trail there. Any tips for a first-timer?",
    )
    val scamScript = listOf(
        "Hello dear, I feel a strong connection with you already. God has brought us together.",
        "I am working on an oil rig so I cannot do video call, network is very poor here. Let's talk on WhatsApp, this app is slow.",
        "I sent you a surprise gift parcel from London with jewellery and some pounds. But it is stuck at Delhi airport customs. They are asking clearance fee of Rs 38,500. Please pay urgently today on this UPI customs.clearance@okicici, I will return it when we meet.",
    )
}
