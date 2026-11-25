package com.vachan.app.data

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.vachan.app.core.model.ConsentPurpose
import com.vachan.app.core.model.ConsentRecord
import com.vachan.app.core.model.ProfileVisibility
import com.vachan.app.core.photos.EncryptedPhotoStore
import com.vachan.app.core.photos.PhotoProcessor
import com.vachan.app.core.security.SecureStore
import com.vachan.app.data.demo.DemoBackend
import com.vachan.app.core.TestProfiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Privacy and robustness guarantees of the on-device backend and photo pipeline. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = android.app.Application::class)
class DemoBackendPrivacyTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var context: Context
    private lateinit var settings: LocalSettingsRepository
    private lateinit var photos: EncryptedPhotoStore
    private lateinit var backend: DemoBackend

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AeadConfig.register()
        val aead = KeysetHandle.newBuilder()
            .addEntry(KeysetHandle.generateEntryFromParameters(PredefinedAeadParameters.AES256_GCM).withRandomId().makePrimary())
            .build().getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        val store = SecureStore(context) { aead }
        settings = LocalSettingsRepository(store, scope)
        photos = EncryptedPhotoStore(context) { aead }
        backend = DemoBackend(store, scope, settings, photos)
        runBlocking {
            backend.sendOtp("+919876543210")
            backend.verifyOtp(DemoBackend.DEMO_OTP)
            settings.update { it.copy(onboarded = true, trustedContactPhone = "+919800000000", consents = mapOf(ConsentPurpose.AI_ASSISTANCE to ConsentRecord(true, 1L))) }
            backend.saveMyProfile(TestProfiles.woman())
        }
    }

    @After
    fun tearDown() = scope.cancel()

    private fun jpeg(w: Int = 800, h: Int = 1000): ByteArray =
        PhotoProcessor.encode(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFB0532F.toInt()) })

    @Test
    fun signOutLeavesNothingOfTheMemberOnTheDevice() = runBlocking {
        val photo = backend.addPhoto(jpeg()).getOrThrow()
        assertTrue(photos.read(photo.id) != null)

        backend.signOut()

        assertNull("session", backend.session.value)
        assertNull("profile", backend.myProfile.value)
        assertTrue("interests", backend.interests.value.isEmpty())
        assertTrue("conversations", backend.conversations.value.isEmpty())
        assertNull("photo file", photos.read(photo.id))
        val s = settings.settings.value
        assertFalse("onboarded flag would skip the next person's consent", s.onboarded)
        assertTrue("consents", s.consents.isEmpty())
        assertEquals("trusted contact", "", s.trustedContactPhone)
    }

    @Test
    fun cancellingDeletionRestoresTheMembersOwnVisibility() = runBlocking {
        val me = backend.myProfile.value!!
        backend.saveMyProfile(me.copy(visibility = ProfileVisibility.VERIFIED_ONLY)).getOrThrow()
        backend.requestDeletion().getOrThrow()
        assertEquals(ProfileVisibility.HIDDEN, backend.myProfile.value!!.visibility)
        backend.cancelDeletion().getOrThrow()
        assertEquals(ProfileVisibility.VERIFIED_ONLY, backend.myProfile.value!!.visibility)
    }

    @Test
    fun onlyRealStoredPhotosCountTowardsAProfile() = runBlocking {
        val me = backend.myProfile.value!!
        // A client claiming a photo it never uploaded must not get credit for having one.
        val saved = backend.saveMyProfile(me.copy(photos = listOf(com.vachan.app.core.model.Photo("ph_fake", seed = 1)))).getOrThrow()
        assertTrue(saved.photos.isEmpty())
        val real = backend.addPhoto(jpeg()).getOrThrow()
        assertEquals(listOf(real.id), backend.myProfile.value!!.photos.map { it.id })
    }

    @Test
    fun photosAreEncryptedAtRestAndBoundToTheirId() = runBlocking {
        val bytes = jpeg()
        photos.write("ph_abc", bytes)
        val onDisk = java.io.File(context.filesDir, "photos/ph_abc.bin").readBytes()
        assertFalse("plaintext JPEG on disk", onDisk.take(3) == bytes.take(3))
        assertArrayEquals(bytes, photos.read("ph_abc"))
        // Swapping files between ids must fail authentication rather than show the wrong photo.
        java.io.File(context.filesDir, "photos/ph_other.bin").writeBytes(onDisk)
        assertNull(photos.read("ph_other"))
    }

    @Test
    fun processedPhotosCarryNoExifMetadata() {
        val out = jpeg(2400, 3200)
        val text = String(out, Charsets.ISO_8859_1)
        assertFalse("EXIF block present", text.contains("Exif\u0000\u0000"))
        val decoded = android.graphics.BitmapFactory.decodeByteArray(out, 0, out.size)
        assertEquals(PhotoProcessor.MAX_EDGE, maxOf(decoded.width, decoded.height))
    }

    @Test
    fun photoSizingIsBounded() {
        assertEquals(1, PhotoProcessor.sampleSizeFor(1600, 1200))
        assertEquals(4, PhotoProcessor.sampleSizeFor(8000, 6000))
        assertEquals(1600 to 1200, PhotoProcessor.scaledSize(4000, 3000))
        assertEquals(800 to 600, PhotoProcessor.scaledSize(800, 600))
    }

    @Test
    fun photoCountIsCapped() = runBlocking {
        repeat(DemoBackend.MAX_PHOTOS) { backend.addPhoto(jpeg(400, 500)).getOrThrow() }
        assertTrue(backend.addPhoto(jpeg(400, 500)).isFailure)
    }
}
