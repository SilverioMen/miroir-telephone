package app.miroir.pairing

import java.net.URLDecoder

/** Contenu du QR code affiche par l'app de bureau. */
data class PairingPayload(
    val addresses: List<String>,
    val port: Int,
    val token: String,
    val fingerprint: String,
) {
    companion object {
        private const val PREFIX = "miroir://pair?"
        private val EMPREINTE = Regex("^[0-9a-f]{64}$")

        fun parse(text: String): PairingPayload? {
            if (!text.startsWith(PREFIX)) return null
            val params = text.removePrefix(PREFIX).split('&').mapNotNull { morceau ->
                val i = morceau.indexOf('=')
                if (i < 0) null else morceau.substring(0, i) to URLDecoder.decode(morceau.substring(i + 1), "UTF-8")
            }.toMap()

            val addresses = params["a"].orEmpty().split(',').filter { it.isNotBlank() }
            val port = params["p"]?.toIntOrNull() ?: return null
            val token = params["t"].orEmpty()
            val fingerprint = params["f"].orEmpty()

            if (addresses.isEmpty() || token.isEmpty()) return null
            if (port !in 1..65535) return null
            if (!EMPREINTE.matches(fingerprint)) return null
            return PairingPayload(addresses, port, token, fingerprint)
        }
    }
}
