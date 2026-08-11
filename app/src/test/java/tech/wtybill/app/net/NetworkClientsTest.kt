package tech.wtybill.app.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class NetworkClientsTest {
    @Test
    fun imageClientSharesPoolAndDispatcherButUsesImageTimeouts() {
        assertSame(NetworkClients.base.connectionPool, NetworkClients.image.connectionPool)
        assertSame(NetworkClients.base.dispatcher, NetworkClients.image.dispatcher)
        assertEquals(15_000, NetworkClients.image.readTimeoutMillis)
        assertEquals(15_000, NetworkClients.image.writeTimeoutMillis)
    }
}
