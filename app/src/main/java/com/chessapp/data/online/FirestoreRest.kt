package com.chessapp.data.online

import com.chessapp.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Minimal Firestore + anonymous-auth client over plain REST. Deliberately avoids
 * the Firebase SDK so no google-services.json is needed: the user pastes their
 * project id and Web API key into Settings (SERVER_SETUP.md) and online play
 * works on the free Spark tier. All calls run on Dispatchers.IO.
 */
class FirestoreRest(
    private val projectId: String,
    private val apiKey: String,
    private val prefs: SettingsRepository
) {
    class HttpError(val code: Int, message: String) : IOException("HTTP $code: $message")

    data class Doc(val fields: JSONObject, val updateTime: String)

    private suspend fun ensureAuth(): SettingsRepository.OnlineAuth = withContext(Dispatchers.IO) {
        val cached = prefs.readAuth()
        val now = System.currentTimeMillis()
        if (cached != null && cached.expiry - 60_000 > now) return@withContext cached
        if (cached != null) {
            // refresh the id token, keeping the SAME uid (game identity)
            val resp = runCatching {
                post(
                    "https://securetoken.googleapis.com/v1/token?key=$apiKey",
                    "grant_type=refresh_token&refresh_token=${enc(cached.refresh)}",
                    form = true, auth = null
                )
            }.getOrNull()
            if (resp != null) {
                val a = SettingsRepository.OnlineAuth(
                    uid = resp.optString("user_id", cached.uid),
                    idToken = resp.getString("id_token"),
                    refresh = resp.optString("refresh_token", cached.refresh),
                    expiry = now + resp.getString("expires_in").toLong() * 1000
                )
                prefs.writeAuth(a); return@withContext a
            }
            // Refresh failed but we HAVE an identity: never silently sign up as a
            // new anonymous user — that would orphan the player out of their own
            // games mid-match. Surface it as a connection problem instead.
            throw IOException("Couldn't refresh your online session \u2014 check your connection and try again")
        }
        // true first run: anonymous sign-up
        val r = post(
            "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$apiKey",
            JSONObject().put("returnSecureToken", true).toString(), form = false, auth = null
        )
        val a = SettingsRepository.OnlineAuth(
            uid = r.getString("localId"),
            idToken = r.getString("idToken"),
            refresh = r.getString("refreshToken"),
            expiry = now + r.getString("expiresIn").toLong() * 1000
        )
        prefs.writeAuth(a); a
    }

    suspend fun myUid(): String = ensureAuth().uid

    private fun docUrl(code: String) =
        "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/games/$code"

    suspend fun getGame(code: String): Doc? = withContext(Dispatchers.IO) {
        val auth = ensureAuth()
        try {
            val r = request("GET", docUrl(code), null, auth.idToken)
            Doc(r.getJSONObject("fields"), r.getString("updateTime"))
        } catch (e: HttpError) {
            if (e.code == 404) null else throw e
        }
    }

    suspend fun createGame(code: String, fields: JSONObject): Boolean = withContext(Dispatchers.IO) {
        val auth = ensureAuth()
        try {
            request("POST",
                "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/games?documentId=$code",
                JSONObject().put("fields", fields), auth.idToken)
            true
        } catch (e: HttpError) {
            if (e.code == 409) false else throw e   // code collision -> caller regenerates
        }
    }

    /** Patch with optimistic concurrency; returns false on a lost race (409). */
    suspend fun patchGame(
        code: String, fields: JSONObject, mask: List<String>, requireUpdateTime: String
    ): Boolean = withContext(Dispatchers.IO) {
        val auth = ensureAuth()
        val params = mask.joinToString("&") { "updateMask.fieldPaths=$it" } +
            "&currentDocument.updateTime=${enc(requireUpdateTime)}"
        try {
            request("PATCH", "${docUrl(code)}?$params",
                JSONObject().put("fields", fields), auth.idToken)
            true
        } catch (e: HttpError) {
            if (e.code == 409 || e.code == 412) false else throw e
        }
    }

    // ---------- plumbing ----------

    private fun post(url: String, body: String, form: Boolean, auth: String?): JSONObject {
        val c = open("POST", url, auth)
        c.setRequestProperty("Content-Type",
            if (form) "application/x-www-form-urlencoded" else "application/json")
        c.doOutput = true
        c.outputStream.use { it.write(body.toByteArray()) }
        return readJson(c)
    }

    private fun request(method: String, url: String, body: JSONObject?, auth: String?): JSONObject {
        val c = open(method, url, auth)
        if (body != null) {
            c.setRequestProperty("Content-Type", "application/json")
            c.doOutput = true
            c.outputStream.use { it.write(body.toString().toByteArray()) }
        }
        return readJson(c)
    }

    private fun open(method: String, url: String, auth: String?): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        if (method == "PATCH") {                    // HttpURLConnection lacks PATCH
            c.requestMethod = "POST"
            c.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        } else c.requestMethod = method
        c.connectTimeout = 10_000; c.readTimeout = 10_000
        if (auth != null) c.setRequestProperty("Authorization", "Bearer $auth")
        return c
    }

    private fun readJson(c: HttpURLConnection): JSONObject {
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)
            ?.bufferedReader()?.readText() ?: ""
        if (code !in 200..299) throw HttpError(code, text.take(300))
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    companion object {
        // ----- Firestore Value encoding helpers -----
        fun str(v: String): JSONObject = JSONObject().put("stringValue", v)
        fun int(v: Long): JSONObject = JSONObject().put("integerValue", v.toString())
        fun arr(items: List<String>): JSONObject = JSONObject().put(
            "arrayValue", JSONObject().put("values", JSONArray(items.map { str(it) }))
        )
        fun getStr(f: JSONObject, k: String, d: String = ""): String =
            f.optJSONObject(k)?.optString("stringValue", d) ?: d
        fun getArr(f: JSONObject, k: String): List<String> {
            val vs = f.optJSONObject(k)?.optJSONObject("arrayValue")
                ?.optJSONArray("values") ?: return emptyList()
            return (0 until vs.length()).map { vs.getJSONObject(it).optString("stringValue") }
        }
        fun getInt(f: JSONObject, k: String): Long =
            f.optJSONObject(k)?.optString("integerValue", "0")?.toLongOrNull() ?: 0L
    }
}
