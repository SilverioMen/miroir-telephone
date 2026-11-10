package app.miroir.net

/**
 * Ne garde que les candidats ICE joignables depuis le Mac. Sur un point d'acces du telephone, l'adresse de la
 * connexion mobile est aussi joignable en apparence, mais les reponses sortent par le reseau mobile.
 */
object IceFilter {
    private val ipv4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    fun adresse(candidat: String): String? = candidat.trim().split(' ').getOrNull(4)

    private fun sousReseau(ip: String): String = ip.substringBeforeLast('.')

    fun garde(candidat: String, adresseMac: String?): Boolean {
        if (adresseMac == null || !ipv4.matches(adresseMac)) return true
        val ip = adresse(candidat) ?: return true
        if (!ipv4.matches(ip)) return true
        return sousReseau(ip) == sousReseau(adresseMac)
    }
}
