package it.meapps.gestionale

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal class VgArticleShare(private val activity: Activity) {
    private val cache = File(activity.cacheDir, "article-share")
    private val lock = Mutex()
    private var pending: Album? = null

    private data class Album(
        val directory: File,
        val count: Int,
        val photos: ArrayList<Uri> = arrayListOf(),
        var bytes: Long = 0
    )

    suspend fun handle(message: String, canLaunch: () -> Boolean): JSONObject = lock.withLock {
        require(message.length <= MAX_ENCODED_PHOTO + 4096) { "Una foto è troppo grande." }
        val input = JSONObject(message)
        if (input.getString("action") == "begin") {
            val count = input.getInt("count")
            require(count in 1..20) { "Numero di foto non valido." }
            pending?.directory?.deleteRecursively()
            cache.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - CACHE_TTL }
                ?.forEach { it.deleteRecursively() }
            val directory = File(cache, UUID.randomUUID().toString())
            check(directory.mkdirs()) { "Impossibile preparare le foto sul telefono." }
            pending = Album(directory, count)
            return@withLock JSONObject().put("session", directory.name)
        }

        val album = pending
        require(album != null && album.directory.name == input.getString("session")) { "Preparazione delle foto scaduta." }
        when (input.getString("action")) {
            "photo" -> {
                val name = input.getString("name")
                val data = input.getString("data")
                require(album.photos.size < album.count && PHOTO_NAME.matches(name)) { "Foto non valida." }
                require(data.length <= MAX_ENCODED_PHOTO) { "Una foto è troppo grande." }
                val bytes = Base64.decode(data, Base64.NO_WRAP)
                require(bytes.isNotEmpty() && album.bytes + bytes.size <= MAX_ALBUM_BYTES) { "L’album supera lo spazio disponibile per la condivisione." }
                val file = File(album.directory, "${album.photos.size + 1}-$name")
                file.writeBytes(bytes)
                album.photos.add(FileProvider.getUriForFile(activity, activity.packageName + ".files", file))
                album.bytes += bytes.size
            }
            "send" -> {
                check(album.photos.size == album.count) { "L’album è incompleto." }
                val caption = input.optString("caption").take(4000)
                val intent = Intent(if (album.count == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/*"
                    if (caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, caption)
                    if (album.count == 1) putExtra(Intent.EXTRA_STREAM, album.photos.first())
                    else putParcelableArrayListExtra(Intent.EXTRA_STREAM, album.photos)
                    clipData = ClipData.newUri(activity.contentResolver, "Foto articolo", album.photos.first()).apply {
                        album.photos.drop(1).forEach { addItem(ClipData.Item(it)) }
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                withContext(Dispatchers.Main) {
                    check(canLaunch() && !activity.isFinishing && !activity.isDestroyed) { "Riapri il pannello e riprova." }
                    activity.startActivity(Intent.createChooser(intent, "Condividi articolo"))
                }
                pending = null
            }
            "cancel" -> {
                album.directory.deleteRecursively()
                pending = null
            }
            else -> error("Operazione non valida.")
        }
        JSONObject()
    }

    private companion object {
        val PHOTO_NAME = Regex("[A-Za-z0-9_-]{1,160}\\.(?:jpe?g|png|gif|webp|bmp|avif|heic)", RegexOption.IGNORE_CASE)
        const val MAX_ENCODED_PHOTO = 24 * 1024 * 1024
        const val MAX_ALBUM_BYTES = 120L * 1024 * 1024
        const val CACHE_TTL = 24L * 60 * 60 * 1000
    }
}
