package io.cloudcauldron.bocan.app.sync

import androidx.test.core.app.ApplicationProvider
import io.cloudcauldron.bocan.app.BocanApplication
import org.junit.rules.ExternalResource

/**
 * Starts a [FakeMac] on the loopback interface in pairing mode and advertises it to the debug
 * build's discovery before the test, and stops it after. The advert reaches discovery whenever
 * it starts listening, so the order against the activity rule does not matter.
 */
class FakeMacRule : ExternalResource() {
    lateinit var mac: FakeMac
        private set

    override fun before() {
        mac = FakeMac(ApplicationProvider.getApplicationContext<BocanApplication>().assets).start()
        mac.publish()
    }

    override fun after() {
        mac.close()
    }
}
