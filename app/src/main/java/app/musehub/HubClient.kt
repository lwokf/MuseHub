package app.musehub

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Agent(val id: String, val name: String, val role: String)
data class Room(val id: String, val name: String, val type: String, val members: List<String>)
data class HubMessage(val id: String, val seq: Long, val from: String, val roomId: String, val taskId: String?, val text: String, val createdAt: String, val replyTo: String? = null, val replyFrom: String? = null, val replyText: String? = null, val source: String? = null)
data class HistoryEntry(val status: String, val by: String, val at: String, val reason: String)
data class HubTask(val id: String, val title: String, val description: String, val assignee: String, val creator: String, val deadline: String?, val status: String, val reason: String, val history: List<HistoryEntry>)
data class HubFile(val id: String, val filename: String, val uploader: String, val taskId: String?, val sha256: String, val size: Long, val createdAt: String)
data class AuditEntry(val time: String, val agent: String, val action: String, val objectId: String, val detail: String)

/** Minimal Hub API client. HttpURLConnection only, no extra network dependency. */
class HubClient(var baseUrl: String, var token: String) {

    private fun cleanBase(): String = baseUrl.trim().trimEnd('/')

    private fun request(method: String, path: String, body: String? = null, extraHeaders: Map<String, String> = emptyMap()): Pair<Int, String> {
        val url = URL(cleanBase() + path)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 20000
            if (token.isNotBlank()) setRequestProperty("X-Hub-Token", token)
            for ((k, v) in extraHeaders) setRequestProperty(k, v)
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { it.write(body) }
            }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        conn.disconnect()
        return code to text
    }

    private fun errText(text: String, code: Int): String {
        return try {
            JSONObject(text).optString("error", "HTTP $code")
        } catch (e: Exception) {
            "HTTP $code"
        }
    }

    private fun <T> ok(code: Int, text: String, parse: (JSONObject) -> T): Result<T> {
        if (code !in 200..299) return Result.failure(Exception(errText(text, code)))
        return try {
            Result.success(parse(JSONObject(text)))
        } catch (e: Exception) {
            Result.failure(Exception("解析失败：${e.message}"))
        }
    }

    private fun jsonObj(s: String): JSONObject = JSONObject(s)
    private fun arr(o: JSONObject, key: String): JSONArray = o.optJSONArray(key) ?: JSONArray()

    private fun parseAgent(o: JSONObject) = Agent(o.optString("id"), o.optString("name"), o.optString("role"))
    private fun parseRoom(o: JSONObject): Room {
        val members = mutableListOf<String>()
        val a = o.optJSONArray("members") ?: JSONArray()
        for (i in 0 until a.length()) members.add(a.optString(i))
        return Room(o.optString("id"), o.optString("name"), o.optString("type"), members)
    }
    private fun parseMessage(o: JSONObject) = HubMessage(
        o.optString("id"), o.optLong("seq"), o.optString("from"), o.optString("room_id"),
        if (o.isNull("task_id")) null else o.optString("task_id"),
        o.optString("text"), o.optString("created_at"),
        if (o.isNull("reply_to")) null else o.optString("reply_to"),
        if (o.isNull("reply_from")) null else o.optString("reply_from"),
        if (o.isNull("reply_text")) null else o.optString("reply_text"),
        if (o.isNull("source")) null else o.optString("source")
    )
    private fun parseTask(o: JSONObject): HubTask {
        val hist = mutableListOf<HistoryEntry>()
        val a = o.optJSONArray("history") ?: JSONArray()
        for (i in 0 until a.length()) {
            val h = a.getJSONObject(i)
            hist.add(HistoryEntry(h.optString("status"), h.optString("by"), h.optString("at"), h.optString("reason")))
        }
        return HubTask(
            o.optString("id"), o.optString("title"), o.optString("description"),
            o.optString("assignee"), o.optString("creator"),
            if (o.isNull("deadline")) null else o.optString("deadline"),
            o.optString("status"), o.optString("reason"), hist
        )
    }
    private fun parseFile(o: JSONObject) = HubFile(
        o.optString("id"), o.optString("filename"), o.optString("uploader"),
        if (o.isNull("task_id")) null else o.optString("task_id"),
        o.optString("sha256"), o.optLong("size"), o.optString("created_at")
    )
    private fun parseAudit(o: JSONObject) = AuditEntry(
        o.optString("time"), o.optString("agent"), o.optString("action"),
        o.optString("object_id"), o.optString("detail")
    )

    suspend fun health(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val (code, text) = request("GET", "/api/health")
            if (code in 200..299) Result.success(Unit) else Result.failure(Exception(errText(text, code)))
        } catch (e: Exception) { Result.failure(Exception("连不上 Hub：${e.message}")) }
    }

    suspend fun getAgents(): Result<List<Agent>> = withContext(Dispatchers.IO) {
        try {
            val (code, text) = request("GET", "/api/agents")
            ok(code, text) { o -> arr(o, "agents").let { a -> (0 until a.length()).map { parseAgent(a.getJSONObject(it)) } } }
        } catch (e: Exception) { Result.failure(Exception("连不上 Hub：${e.message}")) }
    }

    suspend fun getRooms(): Result<List<Room>> = withContext(Dispatchers.IO) {
        try {
            val (code, text) = request("GET", "/api/rooms")
            ok(code, text) { o -> arr(o, "rooms").let { a -> (0 until a.length()).map { parseRoom(a.getJSONObject(it)) } } }
        } catch (e: Exception) { Result.failure(Exception("连不上 Hub：${e.message}")) }
    }

    suspend fun createRoom(name: String, type: String): Result<Room> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("name", name).put("type", type).toString()
            val (code, text) = request("POST", "/api/rooms", body)
            ok(code, text) { parseRoom(it.getJSONObject("room")) }
        } catch (e: Exception) { Result.failure(Exception("连不上 Hub：${e.message}")) }
    }

    suspend fun getMessages(roomId: String, since: Long = 0): Result<List<HubMessage>> = withContext(Dispatchers.IO) {
        try {
            val (code, text) = request("GET", "/api/messages?room_id=${URLEncoder.encode(roomId, "UTF-8")}&since=$since")
            ok(code, text) { o -> arr(o, "messages").let { a -> (0 until a.length()).map { parseMessage(a.getJSONObject(it)) } } }
        } catch (e: Exception) { Result.failure(Exception("连不上 Hub：${e.message}")) }
    }

    suspend fun sendMessage(from: String, roomId: String, text: String, taskId: String? = null, replyTo: String? = null): Result<HubMessage> = withContext(Dispatchers.IO) {
        try {
            // App 里手打的消息记 source=manual；定时轮询代回的走 send_reply.py，记 source=auto。
            val body = JSONObject().put("from", from).put("room_id", roomId).put("text", text).put("source", "manual")
            if (taskId != null) body.put("task_id", taskId)
            if (replyTo != null) body.put("reply_to", replyTo)
            val (code, resp) = request("POST", "/api/messages", body.toString())
            ok(code, resp) { parseMessage(it.getJSONObject("message")) }
        } catch (e: Exception) { Result.failure(Exception("连不上 Hub：${e.message}")) }
    }

    suspend fun getTasks(): Result<List<HubTask>> = withContext(Dispatchers.IO) {
        try {
            val (code, text) = request("GET", "/api/tasks")
            ok(code, text) { o -> arr(o, "tasks").let { a -> (0 until a.length()).map { parseTask(a.getJSONObject(it)) } } }
        } catch (e: Exception) { Result.failure(Exception("连不上 Hub：${e.message}")) }
    }

    suspend fun createTask(title: String, description: String, assignee: String, deadline: String?): Result<HubTask> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("title", title).put("description", description).put("assignee", assignee)
            if (!deadline.isNullOrBlank()) body.put("deadline", deadline)
            val (code, text) = request("POST", "/api/tasks", body.toString())
            ok(code, text) { parseTask(it.getJSONObject("task")) }
        } catch (e: Exception) { Result.failure(Exception("连不上 Hub：${e.message}")) }
    }

    suspend fun setTaskStatus(id: String, status: String, reason: String = ""): Result<HubTask> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("status", status)
            if (reason.isNotBlank()) body.put("reason", reason)
            val (code, text) = request("POST", "/api/tasks/$id/status", body.toString())
            ok(code, text) { parseTask(it.getJSONObject("task")) }
        } catch (e: Exception) { Result.failure(Exception("连不上 Hub：${e.message}")) }
    }

    suspend fun getFiles(): Result<List<HubFile>> = withContext(Dispatchers.IO) {
        try {
            val (code, text) = request("GET", "/api/files")
            ok(code, text) { o -> arr(o, "files").let { a -> (0 until a.length()).map { parseFile(a.getJSONObject(it)) } } }
        } catch (e: Exception) { Result.failure(Exception("连不上 Hub：${e.message}")) }
    }

    /** Multipart upload, written by hand so no extra dependency is needed. */
    suspend fun uploadFile(filename: String, bytes: ByteArray, taskId: String? = null): Result<HubFile> = withContext(Dispatchers.IO) {
        try {
            val boundary = "----MuseHub${System.currentTimeMillis()}"
            val url = URL(cleanBase() + "/api/files")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30000
                readTimeout = 60000
                doOutput = true
                setRequestProperty("X-Hub-Token", token)
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }
            conn.outputStream.use { out ->
                fun w(s: String) { out.write(s.toByteArray(Charsets.UTF_8)) }
                if (taskId != null) {
                    w("--$boundary\r\nContent-Disposition: form-data; name=\"task_id\"\r\n\r\n$taskId\r\n")
                }
                w("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$filename\"\r\nContent-Type: application/octet-stream\r\n\r\n")
                out.write(bytes)
                w("\r\n--$boundary--\r\n")
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            conn.disconnect()
            if (code !in 200..299) return@withContext Result.failure(Exception(errText(text, code)))
            val fileObj = JSONObject(text).getJSONObject("file")
            Result.success(parseFile(fileObj))
        } catch (e: Exception) { Result.failure(Exception("连不上 Hub：${e.message}")) }
    }

    suspend fun downloadFile(id: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val url = URL(cleanBase() + "/api/files/$id/download")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 60000
                if (token.isNotBlank()) setRequestProperty("X-Hub-Token", token)
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                val text = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                conn.disconnect()
                return@withContext Result.failure(Exception(errText(text, code)))
            }
            val bytes = conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            Result.success(bytes)
        } catch (e: Exception) { Result.failure(Exception("连不上 Hub：${e.message}")) }
    }

    suspend fun getAudit(): Result<List<AuditEntry>> = withContext(Dispatchers.IO) {
        try {
            val (code, text) = request("GET", "/api/audit")
            ok(code, text) { o -> arr(o, "audit").let { a -> (0 until a.length()).map { parseAudit(a.getJSONObject(it)) } } }
        } catch (e: Exception) { Result.failure(Exception("连不上 Hub：${e.message}")) }
    }

    suspend fun getAutoReply(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val (code, text) = request("GET", "/api/settings")
            ok(code, text) { it.getJSONObject("settings").optBoolean("auto_reply", false) }
        } catch (e: Exception) { Result.failure(Exception("连不上 Hub：${e.message}")) }
    }

    suspend fun setAutoReply(on: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("auto_reply", on).toString()
            val (code, text) = request("POST", "/api/settings", body)
            ok(code, text) { it.getJSONObject("settings").optBoolean("auto_reply", false) }
        } catch (e: Exception) { Result.failure(Exception("连不上 Hub：${e.message}")) }
    }

    /** Rename a member (display name / role only; id, token, rooms and tasks untouched). */
    suspend fun updateAgent(id: String, name: String, role: String): Result<Agent> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("name", name).put("role", role).toString()
            val (code, text) = request("POST", "/api/agents/${URLEncoder.encode(id, "UTF-8")}", body)
            ok(code, text) { parseAgent(it.getJSONObject("agent")) }
        } catch (e: Exception) { Result.failure(Exception("连不上 Hub：${e.message}")) }
    }

    /** Owner only. Returns the new agent's token on success. */
    suspend fun registerAgent(name: String, role: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("name", name).put("role", role).toString()
            val (code, text) = request("POST", "/api/agents", body)
            if (code !in 200..299) return@withContext Result.failure(Exception(errText(text, code)))
            Result.success(JSONObject(text).optString("token"))
        } catch (e: Exception) { Result.failure(Exception("连不上 Hub：${e.message}")) }
    }
}
