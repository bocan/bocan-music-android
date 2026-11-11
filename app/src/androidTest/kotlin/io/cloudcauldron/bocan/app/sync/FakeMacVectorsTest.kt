package io.cloudcauldron.bocan.app.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The fake Mac's half of the pairing ceremony against the golden vectors the Mac repo shares
 * byte for byte (sync-protocol.md section 4). The app's half is checked against the same file
 * by `PairingCodeTests`; the two halves then meet over the wire in [HermeticSyncJourney]. The
 * file comes from :core:sync's test fixtures, packed into the test APK's assets.
 */
@RunWith(AndroidJUnit4::class)
class FakeMacVectorsTest {
    @Test
    fun theFakeMacDerivesEveryGoldenCodeAndProof() {
        val text = InstrumentationRegistry.getInstrumentation().context.assets.open(VECTORS).use { it.readBytes().decodeToString() }
        val vectors = Json.parseToJsonElement(text).jsonObject.getValue("vectors").jsonArray.map { it.jsonObject }
        assertTrue(vectors.isNotEmpty(), "$VECTORS holds no vectors")
        vectors.forEach { vector ->
            fun field(name: String) = vector.getValue(name).jsonPrimitive.content
            val decode = Base64.getDecoder()
            val code = FakeMac.pairingCode(
                fpMac = field("fpMac"),
                fpPhone = field("fpPhone"),
                noncePhone = decode.decode(field("noncePhoneBase64")),
                nonceMac = decode.decode(field("nonceMacBase64"))
            )
            assertEquals(field("expectedCode"), code, "the code for session ${field("sessionId")}")
            val proof = Base64.getEncoder().encodeToString(FakeMac.confirmProof(code, field("sessionId")))
            assertEquals(field("expectedProofBase64"), proof, "the proof for session ${field("sessionId")}")
        }
    }

    private companion object {
        const val VECTORS = "pairing-vectors.json"
    }
}
