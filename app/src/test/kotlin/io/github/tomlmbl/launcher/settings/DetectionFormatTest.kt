package io.github.tomlmbl.launcher.settings

import io.github.tomlmbl.launcher.context.Detection
import io.github.tomlmbl.launcher.context.Profile.HOME
import io.github.tomlmbl.launcher.context.Profile.OUT
import io.github.tomlmbl.launcher.context.Profile.WORK
import io.github.tomlmbl.launcher.context.Profiles
import io.github.tomlmbl.launcher.context.Ssid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/** Automatic switching in the configuration file: SSIDs never in clear, key loss recorded. */
class DetectionFormatTest {

    /** Fake reversible encryption, standing in for the Keystore. */
    private object Reversed : SecretCodec {
        override fun seal(plain: ByteArray): String = Base64.getEncoder().encodeToString(plain.reversedArray())
        override fun open(sealed: String): Opened =
            runCatching { Base64.getDecoder().decode(sealed).reversedArray() }.map { Opened.Value(it) }.getOrDefault(Opened.Lost)
    }

    /** Keystore temporarily unavailable. */
    private object Busy : SecretCodec {
        override fun seal(plain: ByteArray): String? = null
        override fun open(sealed: String): Opened = Opened.Retry
    }

    private val odd = Ssid.of(byteArrayOf('a'.code.toByte(), '\t'.code.toByte(), '\n'.code.toByte(), 0xff.toByte()))
    private val home = Ssid.of("maison".encodeToByteArray())
    private val config = Config(
        profiles = Profiles(listOf(WORK, HOME, OUT), HOME),
        detection = Detection(enabled = true, networks = mapOf(home to HOME, odd to WORK), acknowledged = HOME),
    )

    @Test
    fun detectionSurvivesARoundTrip() {
        val decoded = ConfigFormat.decode(ConfigFormat.encode(config, Reversed), Reversed)
        assertEquals(config.detection, decoded.detection)
        assertEquals(config, decoded)
    }

    @Test
    fun ssidIsNeverWrittenInClear() {
        val text = ConfigFormat.encode(config, Reversed)
        assertFalse(text.contains("maison"))
        assertFalse(text.contains(home.hex))
    }

    @Test
    fun unreadableNetworksAreDroppedAndTheLossIsKept() {
        val decoded = ConfigFormat.decode(ConfigFormat.encode(config, Reversed), SecretCodec.Unavailable)
        assertTrue(decoded.detection.networks.isEmpty())
        assertTrue(decoded.detection.networksLost)
        assertTrue(decoded.detection.enabled)
        assertEquals(config.profiles, decoded.profiles)
        assertTrue(ConfigFormat.decode(ConfigFormat.encode(decoded, Reversed), Reversed).detection.networksLost)
        assertFalse(ConfigFormat.decode(ConfigFormat.encode(decoded.copy(detection = decoded.detection.lossSeen()), Reversed), Reversed).detection.networksLost)
    }

    @Test
    fun networksThatCannotBeSealedAreNotWrittenAndTheLossIsKept() {
        val text = ConfigFormat.encode(config, SecretCodec.Unavailable)
        assertFalse(text.lines().any { it.startsWith("net\t") })
        assertTrue(ConfigFormat.decode(text, Reversed).detection.networksLost)
    }

    @Test
    fun aPassingKeystoreFailureKeepsTheNetworks() {
        // Read during a transient failure, write, then a normal read: nothing is lost.
        val busy = ConfigFormat.decode(ConfigFormat.encode(config, Reversed), Busy)
        assertTrue(busy.detection.networks.isEmpty())
        assertFalse(busy.detection.networksLost)
        val healed = ConfigFormat.decode(ConfigFormat.encode(busy, Busy), Reversed)
        assertEquals(config.detection, healed.detection)
    }

    @Test
    fun detectionIsOffByDefaultAndOlderFilesStillRead() {
        assertEquals(Detection(), ConfigFormat.decode("launcher-config\t1\nfav\ta\tA\n").detection)
        assertEquals(Detection(), ConfigFormat.decode(ConfigFormat.encode(Config())).detection)
    }
}
