package io.cloudcauldron.bocan.sync.identity

import io.cloudcauldron.bocan.sync.heldCertificate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCommonNameTests {
    @Test
    fun `random names match the protocol format`() {
        repeat(50) {
            val cn = DeviceCommonName.random()
            assertTrue("not valid: $cn", DeviceCommonName.isValid(cn))
            assertTrue(cn.startsWith(DeviceCommonName.PREFIX))
        }
    }

    @Test
    fun `random names vary`() {
        assertNotEquals(DeviceCommonName.random(), DeviceCommonName.random())
    }

    @Test
    fun `isValid rejects malformed names`() {
        assertFalse(DeviceCommonName.isValid("bocan-android-XYZ"))
        assertFalse(DeviceCommonName.isValid("bocan-android-deadbee"))
        assertFalse(DeviceCommonName.isValid("bocan-android-deadbeef0"))
        assertFalse(DeviceCommonName.isValid("bocan-mac-deadbeef"))
    }

    @Test
    fun `a held certificate carries a parseable common name`() {
        val cn = heldCertificate("bocan-android-0badf00d").certificate.subjectX500Principal.name
        assertTrue(cn.contains("CN=bocan-android-0badf00d"))
    }
}
