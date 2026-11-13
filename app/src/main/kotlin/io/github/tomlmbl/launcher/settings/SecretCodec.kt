package io.github.tomlmbl.launcher.settings

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encryption of the sensitive values of the configuration file (SSIDs). Never throws. */
interface SecretCodec {
    /** null: not possible. */
    fun seal(plain: ByteArray): String?
    fun open(sealed: String): Opened

    /** No encryption available: nothing sensitive is written or read. */
    object Unavailable : SecretCodec {
        override fun seal(plain: ByteArray): String? = null
        override fun open(sealed: String): Opened = Opened.Lost
    }
}

/** Result of [SecretCodec.open]. */
sealed interface Opened {
    class Value(val bytes: ByteArray) : Opened
    /** Lost for good: missing key, corrupted value. */
    data object Lost : Opened
    /** Possibly transient failure (Keystore): the encrypted value is kept as is. */
    data object Retry : Opened
}

/**
 * Non-exportable AES-GCM key in the Android Keystore: a copy of the file off the device stays unreadable, and
 * networks do not follow to another device. Output: IV then ciphertext, in Base64.
 * A value already encrypted or read is kept in memory: a transient Keystore failure while writing writes the
 * previous one back instead of erasing the network.
 */
class KeystoreCodec : SecretCodec {

    /** Known ciphertext per clear value (hexadecimal), for this process only. */
    private val known = ConcurrentHashMap<String, String>()

    private val keyStore by lazy { KeyStore.getInstance(PROVIDER).apply { load(null) } }

    private fun existingKey(): SecretKey? = (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey

    private fun newKey(): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).run {
        init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        generateKey()
    }

    override fun seal(plain: ByteArray): String? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, existingKey() ?: newKey()) }
        Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(plain)).also { known[plain.toHex()] = it }
    } catch (e: Exception) {
        if (!e.fromKeystore()) throw e
        known[plain.toHex()]
    }

    /** Never creates a key: without the original key, the value is lost. */
    override fun open(sealed: String): Opened = try {
        val bytes = Base64.getDecoder().decode(sealed)
        val key = if (bytes.size <= IV_SIZE) null else existingKey()
        if (key == null) Opened.Lost
        else {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, IV_SIZE))
            val plain = cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE)
            known[plain.toHex()] = sealed
            Opened.Value(plain)
        }
    } catch (_: IllegalArgumentException) {
        Opened.Lost
    } catch (_: AEADBadTagException) {
        Opened.Lost
    } catch (e: Exception) {
        if (!e.fromKeystore()) throw e
        Opened.Retry
    }

    private fun Exception.fromKeystore() = this is GeneralSecurityException || this is java.io.IOException ||
        this is java.security.ProviderException || this is IllegalArgumentException

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "launcher.networks"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }
}
