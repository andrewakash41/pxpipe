package com.vachan.app.core.safety

/**
 * Finds personal and payment identifiers in free text.
 *
 * Used to (1) nudge people before they share contact details in chat, (2) keep contact details
 * out of public bios, and (3) redact text before it is sent to any AI provider. The server has an
 * equivalent redactor (`functions/src/ai/redact.ts`) — the client copy is defence in depth.
 */
object PiiDetector {

    enum class Kind(val label: String, val token: String) {
        PHONE("phone number", "[PHONE]"),
        EMAIL("email address", "[EMAIL]"),
        UPI("UPI ID", "[UPI]"),
        AADHAAR("Aadhaar-like number", "[ID_NUMBER]"),
        PAN("PAN-like number", "[ID_NUMBER]"),
        CARD("card or account number", "[ACCOUNT]"),
        URL("link", "[LINK]"),
        SOCIAL_HANDLE("social media handle", "[HANDLE]"),
        PINCODE("PIN code", "[PINCODE]"),
        OTP("one-time password", "[OTP]"),
    }

    data class Finding(val kind: Kind, val range: IntRange)

    // Order matters: more specific patterns first so their spans win.
    private val patterns: List<Pair<Kind, Regex>> = listOf(
        Kind.EMAIL to Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"""),
        Kind.UPI to Regex("""\b[A-Za-z0-9._-]{2,256}@(?:ok[a-z]+|ybl|ibl|axl|paytm|upi|apl|yapl|axisbank|icici|sbi|hdfcbank|kotak|barodampay|aubank|jupiteraxis|fam|freecharge|slice|naviaxis|[a-z]{3,10})\b""", RegexOption.IGNORE_CASE),
        Kind.URL to Regex("""\b(?:https?://|www\.)\S+|\b[a-z0-9-]+\.(?:com|in|net|org|io|app|me|link|ly|xyz|apk)(?:/\S*)?\b""", RegexOption.IGNORE_CASE),
        Kind.OTP to Regex("""\b(?:otp|verification code|passcode|code is)\b[^\d]{0,12}\d{4,8}\b""", RegexOption.IGNORE_CASE),
        // Phone with an explicit country code before Aadhaar, which would otherwise claim "+91 98…" (12 digits).
        Kind.PHONE to Regex("""(?:\+|\b00)91[\s-]?[6-9]\d{4}[\s-]?\d{5}\b"""),
        Kind.AADHAAR to Regex("""\b[2-9]\d{3}[\s-]?\d{4}[\s-]?\d{4}\b"""),
        Kind.CARD to Regex("""\b(?:\d[\s-]?){13,19}\b"""),
        Kind.PHONE to Regex("""(?<![\d+])0?[6-9]\d{4}[\s-]?\d{5}(?!\d)"""),
        Kind.PAN to Regex("""\b[A-Z]{5}\d{4}[A-Z]\b"""),
        Kind.SOCIAL_HANDLE to Regex("""(?:^|\s)@[A-Za-z0-9_.]{3,30}\b|\b(?:insta|instagram|ig|snap|snapchat|fb|telegram|tg)\s*(?:id|handle)?\s*[:\-]?\s*@?[A-Za-z0-9_.]{3,30}\b""", RegexOption.IGNORE_CASE),
        Kind.PINCODE to Regex("""\b[1-9]\d{2}\s?\d{3}\b"""),
    )

    fun find(text: String): List<Finding> {
        val taken = mutableListOf<IntRange>()
        val out = mutableListOf<Finding>()
        for ((kind, regex) in patterns) {
            for (m in regex.findAll(text)) {
                val r = m.range
                if (taken.any { it.first <= r.last && r.first <= it.last }) continue
                taken += r
                out += Finding(kind, r)
            }
        }
        return out.sortedBy { it.range.first }
    }

    fun kinds(text: String): Set<Kind> = find(text).map { it.kind }.toSet()

    fun containsContactDetails(text: String): Boolean =
        kinds(text).any { it in setOf(Kind.PHONE, Kind.EMAIL, Kind.UPI, Kind.SOCIAL_HANDLE, Kind.URL) }

    /** Replaces every finding with a neutral token, e.g. "call 98765 43210" → "call [PHONE]". */
    fun redact(text: String): String {
        val findings = find(text)
        if (findings.isEmpty()) return text
        val sb = StringBuilder()
        var cursor = 0
        for (f in findings) {
            if (f.range.first < cursor) continue
            sb.append(text, cursor, f.range.first)
            sb.append(f.kind.token)
            cursor = f.range.last + 1
        }
        sb.append(text, cursor, text.length)
        return sb.toString()
    }
}
