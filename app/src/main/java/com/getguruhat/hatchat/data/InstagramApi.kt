package com.getguruhat.hatchat.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Minimal client for Meta's official "Instagram API with Instagram Login".
 * Works for Instagram Creator and Business accounts only.
 *
 * Docs: https://developers.facebook.com/docs/instagram-platform/instagram-api-with-instagram-login
 *
 * No third-party HTTP library on purpose: the whole network surface of the app
 * is this one file, so it is easy to audit.
 */
class InstagramApi(private val token: String) {

    data class Me(val id: String, val userId: String, val username: String)

    data class Conversation(
        val id: String,
        val updatedTime: String,
        val otherUsername: String?,
        val otherId: String?,
    )

    data class Message(
        val id: String,
        val createdTime: String,
        val fromId: String,
        val fromUsername: String,
        val toId: String?,
        val text: String,
    )

    class ApiException(message: String, val code: Int? = null) : Exception(message)

    suspend fun me(): Me {
        val json = get("/me", mapOf("fields" to "id,user_id,username"))
        return Me(
            id = json.optString("id"),
            userId = json.optString("user_id", json.optString("id")),
            username = json.optString("username"),
        )
    }

    /**
     * Instagram filters conversations *after* paging, so a page can come back empty
     * while later pages still hold chats. Keep following the cursor for a few pages.
     */
    suspend fun conversations(me: Me): List<Conversation> {
        var params = mapOf("platform" to "instagram", "limit" to "50", "fields" to "participants,updated_time")
        val items = mutableListOf<JSONObject>()
        var after: String? = null
        for (page in 0 until MAX_PAGES) {
            val json = try {
                get("/me/conversations", params + listOfNotNull(after?.let { "after" to it }))
            } catch (e: ApiException) {
                // Later pages sometimes fail with Instagram's generic error; keep what we have.
                if (page > 0) {
                    Log.w(TAG, "conversations page ${page + 1} failed, keeping ${items.size}: ${e.message}")
                    break
                }
                if ("fields" !in params) throw e
                // Fall back to the documented minimal call if `participants` is rejected.
                params = params - "fields"
                get("/me/conversations", params)
            }
            json.optJSONArray("data")?.let { arr -> (0 until arr.length()).forEach { items += arr.getJSONObject(it) } }
            after = nextCursor(json) ?: break
        }
        return items.map { c ->
            val people = c.optJSONObject("participants")?.optJSONArray("data")
            val other = people?.let { arr ->
                (0 until arr.length()).map { arr.getJSONObject(it) }
                    .firstOrNull { !me.isMe(it.optString("id")) }
            }
            Conversation(
                id = c.getString("id"),
                updatedTime = c.optString("updated_time"),
                otherUsername = other?.optString("username")?.takeIf { it.isNotEmpty() },
                otherId = other?.optString("id")?.takeIf { it.isNotEmpty() },
            )
        }
    }

    /**
     * Instagram only exposes the 20 most recent messages of a conversation.
     * We list their ids, then fetch each message's details in parallel.
     */
    suspend fun messages(conversationId: String): List<Message> = coroutineScope {
        val json = get("/$conversationId", mapOf("fields" to "messages"))
        val ids = json.optJSONObject("messages")?.optJSONArray("data")?.let { arr ->
            (0 until arr.length()).map { arr.getJSONObject(it).getString("id") }
        } ?: emptyList()

        ids.map { id ->
            async {
                try {
                    val m = get("/$id", mapOf("fields" to "id,created_time,from,to,message"))
                    val from = m.optJSONObject("from")
                    Message(
                        id = m.optString("id"),
                        createdTime = m.optString("created_time"),
                        fromId = from?.optString("id").orEmpty(),
                        fromUsername = from?.optString("username").orEmpty(),
                        toId = m.optJSONObject("to")?.optJSONArray("data")
                            ?.optJSONObject(0)?.optString("id"),
                        text = m.optString("message").ifEmpty { "[attachment / unsupported message]" },
                    )
                } catch (e: ApiException) {
                    null // Older messages are reported as "deleted" by the API.
                }
            }
        }.awaitAll().filterNotNull().sortedBy { it.createdTime }
    }

    /**
     * Sends a text DM. Instagram only allows replies within 24 hours of the other
     * person's last message; [humanAgent] extends that to 7 days.
     */
    suspend fun send(recipientId: String, text: String, humanAgent: Boolean) {
        val body = JSONObject()
            .put("recipient", JSONObject().put("id", recipientId))
            .put("message", JSONObject().put("text", text))
        if (humanAgent) {
            body.put("messaging_type", "MESSAGE_TAG").put("tag", "HUMAN_AGENT")
        }
        post("/me/messages", body)
    }

    /**
     * Raw replies from the account and conversation endpoints, for troubleshooting.
     * The token is stripped from the text (paging links contain it), so it is safe to share.
     */
    suspend fun diagnostics(): String {
        val out = StringBuilder()
        fun section(label: String, body: String) = out.append(label).append('\n').append(body).append("\n\n")

        try {
            section("GET /me?fields=user_id,username,account_type",
                get("/me", mapOf("fields" to "user_id,username,account_type")).toString(2))
        } catch (e: Exception) {
            section("GET /me", "ERROR: ${e.message}")
        }

        // Walk the conversation pages and summarise each one.
        val params = mapOf("platform" to "instagram", "limit" to "50", "fields" to "participants,updated_time")
        var after: String? = null
        var total = 0
        for (page in 1..MAX_PAGES) {
            val label = "GET /me/conversations page $page"
            val json = try {
                get("/me/conversations", params + listOfNotNull(after?.let { "after" to it }))
            } catch (e: Exception) {
                section(label, "ERROR: ${e.message}")
                break
            }
            val data = json.optJSONArray("data")
            val count = data?.length() ?: 0
            total += count
            after = nextCursor(json)
            section(label, "$count conversation(s)" +
                (if (count > 0) "\nfirst: ${data!!.getJSONObject(0).toString(2)}" else "") +
                "\nmore pages: ${if (after != null) "yes" else "no"}")
            if (after == null) break
        }
        out.append("Total conversations returned: $total")

        // Strip the token from paging URLs first, then anywhere else it might appear.
        return out.toString()
            .replace(Regex("access_token=[^&\"\\s]+"), "access_token=<key hidden>")
            .replace(token, "<key hidden>")
    }

    private fun nextCursor(json: JSONObject): String? =
        json.optJSONObject("paging")?.takeIf { it.has("next") }
            ?.optJSONObject("cursors")?.optString("after")?.takeIf { it.isNotEmpty() }

    /** Swaps a long-lived token (60 days) for a fresh one. Returns the new token. */
    suspend fun refreshToken(): String {
        val json = get("/refresh_access_token", mapOf("grant_type" to "ig_refresh_token"), versioned = false)
        return json.optString("access_token").ifEmpty { throw ApiException("Refresh returned no token") }
    }

    private suspend fun get(
        path: String,
        params: Map<String, String>,
        versioned: Boolean = true,
    ): JSONObject = withContext(Dispatchers.IO) {
        val query = (params + ("access_token" to token)).entries.joinToString("&") { (k, v) ->
            "${enc(k)}=${enc(v)}"
        }
        val conn = URL("${base(versioned)}$path?$query").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        read(conn, "GET $path ${params.keys.joinToString(",")}")
    }

    private suspend fun post(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val conn = URL("${base(true)}$path").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        read(conn, "POST $path")
    }

    /**
     * Logs go to logcat (`adb logcat -s HatChat`): the request path, status and size,
     * plus Instagram's full error on failures. Never the key or message text.
     */
    private fun read(conn: HttpURLConnection, label: String): JSONObject {
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = if (text.isBlank()) JSONObject() else JSONObject(text)
            json.optJSONObject("error")?.let { err ->
                Log.w(TAG, "$label -> HTTP $code ${err.toString().replace(token, "<key hidden>")}")
                val detail = listOfNotNull(
                    err.optInt("code").takeIf { it != 0 }?.let { "code $it" },
                    err.optInt("error_subcode").takeIf { it != 0 }?.let { "subcode $it" },
                    err.optString("type").takeIf { it.isNotEmpty() },
                    err.optString("fbtrace_id").takeIf { it.isNotEmpty() }?.let { "trace $it" },
                ).joinToString(", ")
                throw ApiException(
                    err.optString("message", "Instagram error") + if (detail.isNotEmpty()) " ($detail)" else "",
                    err.optInt("code"),
                )
            }
            Log.d(TAG, "$label -> HTTP $code, ${json.optJSONArray("data")?.length()?.let { "$it item(s)" } ?: "${text.length} bytes"}")
            if (code !in 200..299) throw ApiException("HTTP $code", code)
            return json
        } finally {
            conn.disconnect()
        }
    }

    private fun base(versioned: Boolean) = if (versioned) "$HOST/$VERSION" else HOST

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    companion object {
        const val HOST = "https://graph.instagram.com"
        const val VERSION = "v26.0"
        private const val MAX_PAGES = 10
        private const val TAG = "HatChat"
    }
}

fun InstagramApi.Me.isMe(id: String?) = id != null && (id == this.id || id == this.userId)
