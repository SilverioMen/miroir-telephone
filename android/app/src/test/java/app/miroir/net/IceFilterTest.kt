package app.miroir.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IceFilterTest {
    private fun candidat(ip: String) = "candidate:1 1 udp 2122260223 $ip 51990 typ host generation 0"

    @Test
    fun `garde un candidat du meme reseau que le Mac`() {
        assertTrue(IceFilter.garde(candidat("10.219.15.168"), "10.219.15.139"))
    }

    @Test
    fun `ecarte un candidat IPv4 d'un autre reseau, comme la connexion mobile`() {
        assertFalse(IceFilter.garde(candidat("10.76.120.213"), "10.219.15.139"))
    }

    @Test
    fun `garde les candidats non IPv4 pour ne pas casser les autres reseaux`() {
        assertTrue(IceFilter.garde("candidate:1 1 udp 1 fe80::1 5000 typ host", "10.219.15.139"))
    }

    @Test
    fun `sans adresse du Mac on garde tout`() {
        assertTrue(IceFilter.garde(candidat("10.76.120.213"), null))
    }

    @Test
    fun `extrait l'adresse d'un candidat`() {
        assertEquals("10.1.2.3", IceFilter.adresse(candidat("10.1.2.3")))
    }
}
