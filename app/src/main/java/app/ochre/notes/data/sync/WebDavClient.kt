package app.ochre.notes.data.sync

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** Minimal WebDAV surface: enough for folders, small objects and a health check. */
class WebDavClient(baseUrl: String, username: String, password: String) {

    private val base = baseUrl.trim().trimEnd('/')
    private val root = "$base/ochre"
    private val authorization = Credentials.basic(username.ifBlank { "ochre" }, password)
    private val binary = "application/octet-stream".toMediaType()
    private val xml = "application/xml".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun ensureRoot() {
        mkcol(root, "create your folder")
        mkcol("$root/objects", "create the notes folder")
        mkcol("$root/media", "create the media folder")
    }

    fun ping() {
        val body = """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>"""
            .toRequestBody(xml)
        val request = Request.Builder()
            .url("$base/")
            .header("Authorization", authorization)
            .method("PROPFIND", body)
            .header("Depth", "0")
            .build()
        execute(request, "reach your WebDAV server")
    }

    fun get(path: String): ByteArray? {
        val request = Request.Builder()
            .url(url(path))
            .header("Authorization", authorization)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) throw failure(response, "read $path")
            return response.body?.bytes()
        }
    }

    fun put(path: String, bytes: ByteArray) {
        val request = Request.Builder()
            .url(url(path))
            .header("Authorization", authorization)
            .put(bytes.toRequestBody(binary))
            .build()
        execute(request, "write $path")
    }

    private fun mkcol(target: String, action: String) {
        val request = Request.Builder()
            .url("$target/")
            .header("Authorization", authorization)
            .method("MKCOL", null)
            .build()
        client.newCall(request).execute().use { response ->
            // 405 means the collection already exists.
            if (!response.isSuccessful && response.code != 405) throw failure(response, action)
        }
    }

    private fun execute(request: Request, action: String) {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw failure(response, action)
        }
    }

    private fun url(path: String) = if (path.isEmpty()) "$root/" else "$root/$path"

    private fun failure(response: Response, action: String): IOException {
        val hint = when (response.code) {
            401, 403 -> "Check the username and password."
            404 -> "The folder was not found."
            405 -> "The server does not allow this action."
            in 500..599 -> "The server had a problem."
            else -> null
        }
        return IOException(
            buildString {
                append("Could not $action (HTTP ${response.code})")
                if (hint != null) append(". ").append(hint)
            },
        )
    }
}