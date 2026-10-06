package io.github.danyk20.cardholder.core.security.keystore

import android.app.KeyguardManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.danyk20.cardholder.core.security.AuthenticationRequiredException
import io.github.danyk20.cardholder.core.security.DeviceNotSecureException
import io.github.danyk20.cardholder.core.security.KeyInvalidatedException
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreKeyWrapperTest {
    private val aesAlias = "test.aes"
    private val rsaAlias = "test.rsa"
    private val dataKey = ByteArray(32) { it.toByte() }

    @After
    fun tearDown() {
        androidKeyStore().apply {
            deleteEntry(aesAlias)
            deleteEntry(rsaAlias)
        }
    }

    @Test
    fun aesWrapperRoundTrips() {
        val wrapper = KeystoreAesKeyWrapper(aesAlias)

        assertContentEquals(dataKey, wrapper.unwrap(wrapper.wrap(dataKey)))
    }

    @Test
    fun aesWrapperReportsMissingKeyAsInvalidated() {
        val wrapped = KeystoreAesKeyWrapper(aesAlias).wrap(dataKey)
        androidKeyStore().deleteEntry(aesAlias)

        assertFailsWith<KeyInvalidatedException> { KeystoreAesKeyWrapper(aesAlias).unwrap(wrapped) }
    }

    @Test
    fun rsaWrapperRequiresAuthenticationToUnwrap() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wrapper = KeystoreRsaKeyWrapper(rsaAlias)
        if (!context.getSystemService(KeyguardManager::class.java).isDeviceSecure) {
            assertFailsWith<DeviceNotSecureException> { wrapper.wrap(dataKey) }
            return
        }

        val wrapped = wrapper.wrap(dataKey)

        assertFailsWith<AuthenticationRequiredException> { wrapper.unwrap(wrapped) }
    }
}
