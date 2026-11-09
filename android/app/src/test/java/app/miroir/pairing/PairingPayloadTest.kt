package app.miroir.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PairingPayloadTest {
    private val empreinte = "a".repeat(64)

    @Test
    fun `parse accepte un QR valide et renvoie la liste d'adresses dans l'ordre`() {
        val p = PairingPayload.parse("miroir://pair?a=192.168.1.20,10.8.0.2&p=51234&t=abcDEF_-123&f=$empreinte")
        assertNotNull(p)
        assertEquals(listOf("192.168.1.20", "10.8.0.2"), p!!.addresses)
        assertEquals(51234, p.port)
        assertEquals("abcDEF_-123", p.token)
        assertEquals(empreinte, p.fingerprint)
    }

    @Test
    fun `parse renvoie null si le schema n'est pas miroir`() {
        assertNull(PairingPayload.parse("https://pair?a=1.2.3.4&p=1234&t=abc&f=$empreinte"))
    }

    @Test
    fun `parse renvoie null si le port est hors 1 a 65535`() {
        assertNull(PairingPayload.parse("miroir://pair?a=1.2.3.4&p=0&t=abc&f=$empreinte"))
        assertNull(PairingPayload.parse("miroir://pair?a=1.2.3.4&p=70000&t=abc&f=$empreinte"))
    }

    @Test
    fun `parse renvoie null si l'empreinte n'a pas 64 caracteres hexadecimaux`() {
        assertNull(PairingPayload.parse("miroir://pair?a=1.2.3.4&p=1234&t=abc&f=abcd"))
        assertNull(PairingPayload.parse("miroir://pair?a=1.2.3.4&p=1234&t=abc&f=${"z".repeat(64)}"))
    }

    @Test
    fun `parse renvoie null sans adresse ou sans jeton`() {
        assertNull(PairingPayload.parse("miroir://pair?a=&p=1234&t=abc&f=$empreinte"))
        assertNull(PairingPayload.parse("miroir://pair?a=1.2.3.4&p=1234&t=&f=$empreinte"))
    }
}
