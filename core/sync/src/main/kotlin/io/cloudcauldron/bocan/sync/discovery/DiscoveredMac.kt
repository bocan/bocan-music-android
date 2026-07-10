package io.cloudcauldron.bocan.sync.discovery

import java.net.InetAddress

/**
 * A Mac advertising `_bocansync._tcp` on the LAN, as parsed from its mDNS TXT
 * record (sync-protocol.md section 1). Identity is the certificate fingerprint,
 * never the service name, which may collide.
 */
data class DiscoveredMac(
    val serviceName: String,
    val host: InetAddress,
    val port: Int,
    val fingerprint: String,
    val pairingMode: Boolean,
    val protocolVersion: Int
)
