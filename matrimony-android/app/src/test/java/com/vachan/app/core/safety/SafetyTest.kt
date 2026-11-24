package com.vachan.app.core.safety

import com.vachan.app.core.safety.PiiDetector.Kind
import com.vachan.app.core.safety.ScamDetector.Level
import com.vachan.app.core.safety.ScamDetector.Signal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScamDetectorTest {
    private val detector = ScamDetector()

    @Test
    fun `ordinary conversation is not flagged`() {
        listOf(
            "Hi! I saw you also love trekking. Have you done the Kalsubai trail?",
            "My parents are visiting next week, maybe we can plan a coffee after that?",
            "Haha yes, filter coffee is the best. Which part of Chennai are you from?",
            "I work as a software engineer and I'm learning classical music on weekends.",
        ).forEach { msg ->
            assertEquals("false positive for: $msg", Level.NONE, detector.assess(msg).level)
        }
    }

    @Test
    fun `customs parcel scam is high risk`() {
        val a = detector.assess("I sent you a gift from London but it is stuck at Delhi customs. You need to pay the clearance fee of Rs 45,000 today")
        assertEquals(Level.HIGH, a.level)
        assertTrue(Signal.CUSTOMS_PARCEL in a.signals)
        assertNotNull(a.primaryAdvice)
    }

    @Test
    fun `investment pitch is high risk`() {
        val a = detector.assess("My uncle is an expert in crypto trading, I can teach you. Guaranteed returns on this investment platform")
        assertEquals(Level.HIGH, a.level)
        assertTrue(Signal.INVESTMENT in a.signals)
    }

    @Test
    fun `hinglish money request with urgency is high risk`() {
        val a = detector.assess("Please jaldi 20000 paise bhej do, hospital mein hoon")
        assertEquals(Level.HIGH, a.level)
        assertTrue(Signal.MONEY_REQUEST in a.signals)
    }

    @Test
    fun `OTP request is high risk`() {
        assertEquals(Level.HIGH, detector.assess("Can you share the OTP you just received? It's for my bank verification").level)
    }

    @Test
    fun `early off-platform pressure is flagged, later less so`() {
        val msg = "Let's continue on WhatsApp, this app is slow"
        assertEquals(Level.CAUTION, detector.assess(msg, daysSinceMatch = 1).level)
        assertEquals(Level.CAUTION, detector.assess(msg, daysSinceMatch = 30).level)
        assertTrue(detector.assess(msg, daysSinceMatch = 1).score > detector.assess(msg, daysSinceMatch = 30).score)
    }

    @Test
    fun `UPI handle in text counts as payment details`() {
        val a = detector.assess("my id is rahul.k@okaxis")
        assertTrue(Signal.PAYMENT_DETAILS in a.signals)
    }

    @Test
    fun `outgoing nudges protect the sender`() {
        assertNotNull(detector.outgoingNudge("You can call me on 98765 43210"))
        assertTrue(detector.outgoingNudge("my otp is 482913")!!.contains("OTP"))
        assertNull(detector.outgoingNudge("Sounds great, see you Saturday!"))
    }
}

class PiiDetectorTest {
    @Test
    fun `finds Indian phone numbers in common formats`() {
        listOf("9876543210", "98765 43210", "+91 98765 43210", "+919876543210", "098765-43210").forEach {
            assertTrue("missed $it", Kind.PHONE in PiiDetector.kinds("call me $it please"))
        }
    }

    @Test
    fun `redacts contact and ID details`() {
        val text = "Mail me at priya.s@gmail.com or pay priya@ybl. Aadhaar 2345 6789 0123, PAN ABCDE1234F, insta @priya_travels"
        val out = PiiDetector.redact(text)
        assertFalse(out.contains("gmail"))
        assertFalse(out.contains("ybl"))
        assertFalse(out.contains("6789"))
        assertFalse(out.contains("ABCDE1234F"))
        assertFalse(out.contains("priya_travels"))
        assertTrue(out.contains("[EMAIL]"))
        assertTrue(out.contains("[UPI]"))
    }

    @Test
    fun `does not treat ordinary numbers and words as PII`() {
        assertEquals(emptySet<Kind>(), PiiDetector.kinds("I am 29 years old, 5 ft 8, and have 2 siblings."))
        assertFalse(PiiDetector.containsContactDetails("I love cooking biryani and reading on weekends."))
    }

    @Test
    fun `bios with contact details are detected`() {
        assertTrue(PiiDetector.containsContactDetails("Whatsapp me 9876543210"))
        assertTrue(PiiDetector.containsContactDetails("Find me at www.example.com"))
    }
}
