package com.vachan.app.core.safety

/**
 * On-device Safety Copilot.
 *
 * Recognises the scripts used in matrimonial fraud in India (from police case reports and
 * cybercrime advisories): fast intimacy → move off-platform → a "problem" that needs money
 * (customs fee for a gift parcel, medical emergency, visa fee) or an "investment opportunity".
 * Includes common Hinglish phrasing.
 *
 * Runs entirely on the phone. Message text is never uploaded for this check.
 */
class ScamDetector {

    enum class Signal(val weight: Int, val advice: String) {
        MONEY_REQUEST(
            40,
            "They're asking for money. Genuine matches don't ask for money, loans or help with payments — especially before meeting.",
        ),
        PAYMENT_DETAILS(
            30,
            "This message contains payment details (UPI, bank or card). Never send money to someone you haven't met in person.",
        ),
        CUSTOMS_PARCEL(
            45,
            "\"A gift is stuck at customs, pay the fee\" is one of the most common matrimony scams. Customs never asks for fees over chat or UPI.",
        ),
        INVESTMENT(
            45,
            "Crypto, trading or \"guaranteed returns\" tips from a match are a well-known scam. Don't install apps or invest on their advice.",
        ),
        GIFT_CARD(35, "Requests for gift cards or recharge codes are a scam pattern."),
        OTP_REQUEST(50, "Never share an OTP or verification code with anyone. It can be used to take over your accounts."),
        OFF_PLATFORM(
            15,
            "They want to move to another app quickly. Staying here keeps you protected by reporting, blocking and safety checks.",
        ),
        URGENCY(10, "Pressure and urgency are classic manipulation tactics. Take your time."),
        EMERGENCY_STORY(20, "Sudden emergencies that need money are a common scam story. Verify independently before reacting."),
        INTIMATE_REQUEST(
            40,
            "Requests for private photos or video calls can lead to blackmail. You can report this and we'll review it.",
        ),
        SUSPICIOUS_LINK(25, "Be careful with links and app downloads (APK files) sent in chat."),
        ABROAD_PERSONA(10, "Overseas job + can't video call + needs help is a frequent fake-persona pattern. Ask for a live video call here."),
    }

    enum class Level { NONE, CAUTION, HIGH }

    data class Assessment(val level: Level, val signals: Set<Signal>, val score: Int) {
        val primaryAdvice: String? get() = signals.maxByOrNull { it.weight }?.advice
    }

    private val rules: Map<Signal, List<Regex>> = mapOf(
        Signal.MONEY_REQUEST to listOf(
            r("""\b(send|transfer|lend|give|need|pay|deposit)\b.{0,30}\b(money|rs\.?|₹|rupees|inr|amount|funds|payment|loan|dollars|usd|lakh|thousand|\d{3,})"""),
            r("""\b(paise|paisa|rupaye|rupay)\b.{0,25}\b(bhej|bhejo|bhejdo|chahiye|de do|dedo|transfer)"""),
            r("""\b(bhej|transfer)\s*(do|dena|kar do|kardo)\b.{0,20}(paise|paisa|amount|₹|rs)"""),
            r("""\budhaar\b|\bborrow\b.{0,20}\bmoney\b|\bcan you (help|support) me (with|financially)"""),
        ),
        Signal.PAYMENT_DETAILS to listOf(
            r("""\b(upi|gpay|google pay|phonepe|paytm|bhim)\b"""),
            r("""\b(ifsc|account (number|no)|a/c|bank details|wire transfer|western union|moneygram)\b"""),
        ),
        Signal.CUSTOMS_PARCEL to listOf(
            r("""\b(customs?|clearance|parcel|package|courier|consignment)\b.{0,60}\b(fee|charge|duty|tax|pay|release|stuck|held|seized)"""),
            r("""\b(gift|parcel)\b.{0,40}\b(airport|delhi airport|mumbai airport|stuck)"""),
            r("""\bdiplomatic (agent|courier)\b"""),
        ),
        Signal.INVESTMENT to listOf(
            r("""\b(crypto|bitcoin|btc|usdt|ethereum|forex|binary option|trading app|trading platform|investment platform|mining pool)\b"""),
            r("""\b(guaranteed|assured|double)\b.{0,20}\b(return|profit|money|income)"""),
            r("""\b(invest|investment)\b.{0,40}\b(app|platform|opportunity|scheme|tips?)\b"""),
            r("""\b(my uncle|my aunt|my mentor|insider)\b.{0,40}\b(trading|stock|crypto|market)"""),
        ),
        Signal.GIFT_CARD to listOf(
            r("""\b(gift ?card|google play card|amazon (pay )?card|itunes card|steam card|recharge code|voucher code)\b"""),
        ),
        Signal.OTP_REQUEST to listOf(
            r("""\b(share|send|tell|forward|give)\b.{0,25}\b(otp|verification code|one.?time password|6.?digit code|code you (got|received))"""),
            r("""\botp\b.{0,20}\b(bata|batao|bhejo|send)"""),
        ),
        Signal.OFF_PLATFORM to listOf(
            r("""\b(whatsapp|whats app|telegram|signal app|snapchat|hangouts|imo|wechat|line app)\b"""),
            r("""\b(my (number|no\.?|contact) is|call me on|text me on|message me on|add me on)\b"""),
            r("""\bdelete (this|the) app\b|\blet'?s (talk|chat) (outside|elsewhere|privately)"""),
        ),
        Signal.URGENCY to listOf(
            r("""\b(urgent|urgently|immediately|right now|asap|today itself|within an hour|jaldi|abhi ke abhi)\b"""),
            r("""\bdon'?t tell (anyone|your (family|parents))\b|\bkeep (this|it) (secret|between us)\b"""),
        ),
        Signal.EMERGENCY_STORY to listOf(
            r("""\b(hospital|accident|surgery|icu|medical emergency|operation)\b.{0,50}\b(money|pay|fees?|bill|help)"""),
            r("""\b(stuck|stranded|detained|arrested|visa)\b.{0,40}\b(money|pay|fees?|fine|help)"""),
        ),
        Signal.INTIMATE_REQUEST to listOf(
            r("""\b(send|share)\b.{0,20}\b(nude|naked|private|intimate|hot|sexy)\b.{0,15}\b(pic|pics|photo|photos|video|selfie)"""),
            r("""\b(private|nude|naked)\s+video\s+call\b"""),
        ),
        Signal.SUSPICIOUS_LINK to listOf(
            r("""\.apk\b|\bdownload (this|the) app\b|\bbit\.ly\b|\btinyurl\b|\bt\.me/"""),
        ),
        Signal.ABROAD_PERSONA to listOf(
            r("""\b(oil rig|offshore|un peacekeeping|army doctor|navy|deployed|working in (dubai|uk|usa|canada|syria))\b"""),
            r("""\b(can'?t|cannot|unable to) (video ?call|turn on (my )?camera)\b"""),
        ),
    )

    /**
     * @param daysSinceMatch early-stage conversations get a stricter threshold for off-platform
     * pressure, since moving chats elsewhere within the first days is a strong scam signal.
     */
    fun assess(text: String, daysSinceMatch: Int = 30): Assessment {
        val normalised = text.lowercase()
        val signals = rules.filter { (_, patterns) -> patterns.any { it.containsMatchIn(normalised) } }.keys.toMutableSet()

        // Payment handles in the text itself (a UPI ID, bank or card number) are a payment signal too.
        val pii = PiiDetector.kinds(text)
        if (PiiDetector.Kind.UPI in pii || PiiDetector.Kind.CARD in pii) signals += Signal.PAYMENT_DETAILS

        var score = signals.sumOf { it.weight }
        if (Signal.OFF_PLATFORM in signals && daysSinceMatch <= 3) score += 15
        // Combinations are much stronger than any single signal.
        if (Signal.URGENCY in signals && (Signal.MONEY_REQUEST in signals || Signal.EMERGENCY_STORY in signals)) score += 20

        val level = when {
            score >= 40 -> Level.HIGH
            score >= 15 -> Level.CAUTION
            else -> Level.NONE
        }
        return Assessment(level, signals, score)
    }

    /** Checks what *I* am about to send, to nudge before sharing contact or payment details. */
    fun outgoingNudge(text: String): String? {
        val kinds = PiiDetector.kinds(text)
        return when {
            PiiDetector.Kind.OTP in kinds || OTP_SHARE.containsMatchIn(text.lowercase()) ->
                "Looks like you're sharing a code. Never share OTPs — not even with a match."
            PiiDetector.Kind.UPI in kinds || PiiDetector.Kind.CARD in kinds ->
                "You're about to share payment details. Please don't send or receive money from matches you haven't met."
            PiiDetector.Kind.AADHAAR in kinds || PiiDetector.Kind.PAN in kinds ->
                "This looks like an ID number. Keep government ID numbers private."
            PiiDetector.Kind.PHONE in kinds || PiiDetector.Kind.EMAIL in kinds ->
                "Want to share your number? Use \"Share contact\" instead — you can take it back any time."
            PiiDetector.Kind.PINCODE in kinds ->
                "This may reveal where you live. Consider meeting in a public place first."
            else -> null
        }
    }

    private companion object {
        fun r(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)
        val OTP_SHARE = Regex("""\b(my|the) otp (is|was)\b""")
    }
}
