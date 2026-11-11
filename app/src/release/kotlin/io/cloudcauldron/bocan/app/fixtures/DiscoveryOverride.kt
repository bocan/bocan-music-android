package io.cloudcauldron.bocan.app.fixtures

import io.cloudcauldron.bocan.sync.discovery.NsdServiceBrowser

/** Release builds carry no end-to-end override: discovery is always the real mDNS browse. */
internal fun discoveryBrowser(real: NsdServiceBrowser): NsdServiceBrowser = real
