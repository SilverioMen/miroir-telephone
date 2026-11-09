package app.miroir.net

import org.json.JSONObject

/** Messages de signalisation echanges avec le recepteur (voir le protocole du plan). */
sealed interface SignalMessage {
    fun toJson(): String

    data class Offer(val sdp: String) : SignalMessage {
        override fun toJson() = JSONObject().put("type", "offer").put("sdp", sdp).toString()
    }

    data class Answer(val sdp: String) : SignalMessage {
        override fun toJson() = JSONObject().put("type", "answer").put("sdp", sdp).toString()
    }

    data class Ice(val candidate: String, val sdpMid: String?, val sdpMLineIndex: Int) : SignalMessage {
        override fun toJson(): String {
            val c = JSONObject().put("candidate", candidate).put("sdpMid", sdpMid).put("sdpMLineIndex", sdpMLineIndex)
            return JSONObject().put("type", "ice").put("candidate", c).toString()
        }
    }

    data object Bye : SignalMessage {
        override fun toJson() = JSONObject().put("type", "bye").toString()
    }

    companion object {
        fun parse(text: String): SignalMessage? = try {
            val o = JSONObject(text)
            when (o.getString("type")) {
                "offer" -> Offer(o.getString("sdp"))
                "answer" -> Answer(o.getString("sdp"))
                "ice" -> o.getJSONObject("candidate").let {
                    Ice(it.getString("candidate"), it.optString("sdpMid").ifEmpty { null }, it.optInt("sdpMLineIndex", 0))
                }
                "bye" -> Bye
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
