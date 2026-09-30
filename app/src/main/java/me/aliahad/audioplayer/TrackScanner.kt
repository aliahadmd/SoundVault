package me.aliahad.audioplayer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class AudioTrack(
    val title: String,
    val uri: Uri,
    /** Path relative to the chosen folder, e.g. "Album/01 Intro.mp3". */
    val relativePath: String = title,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
    val fileSizeBytes: Long? = null
)

/**
 * Builds a playlist from a Storage Access Framework tree.
 *
 * Children are listed with one [DocumentsContract] query per directory (DocumentFile issues a
 * separate query for every property of every file), and tag metadata is read with bounded
 * parallelism instead of one file at a time.
 */
class TrackScanner(private val context: Context) {

    private data class AudioDocument(val uri: Uri, val relativePath: String, val sizeBytes: Long?)

    suspend fun scan(treeUri: Uri): List<AudioTrack> = withContext(Dispatchers.IO) {
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return@withContext emptyList()
        val documents = mutableListOf<AudioDocument>()
        collect(treeUri, rootId, prefix = "", into = documents)

        val permits = Semaphore(METADATA_PARALLELISM)
        coroutineScope {
            documents.map { document ->
                async { permits.withPermit { readTrack(document) } }
            }.awaitAll()
        }.sortedWith(compareBy(NaturalOrderComparator) { it.relativePath })
    }

    private fun collect(treeUri: Uri, parentId: String, prefix: String, into: MutableList<AudioDocument>) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val directories = mutableListOf<Pair<String, String>>()
        try {
            context.contentResolver.query(childrenUri, PROJECTION, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    val mimeType = cursor.getString(2)
                    val size = if (cursor.isNull(3)) null else cursor.getLong(3)
                    when {
                        mimeType == DocumentsContract.Document.MIME_TYPE_DIR -> {
                            if (!name.startsWith(".")) directories += documentId to "$prefix$name/"
                        }
                        isPlayableAudio(name, mimeType) -> into += AudioDocument(
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                            relativePath = "$prefix$name",
                            sizeBytes = size?.takeIf { it >= 0 }
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // A revoked grant or a removed volume must not crash the scan; skip what we cannot read.
            Log.w(TAG, "Could not list $childrenUri", e)
            return
        }
        directories.forEach { (id, path) -> collect(treeUri, id, path, into) }
    }

    private fun readTrack(document: AudioDocument): AudioTrack {
        val fileName = document.relativePath.substringAfterLast('/')
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var durationMs: Long? = null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, document.uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (_: Exception) {
            // Unreadable tags (or an unplayable file) fall back to the file name; playback reports real errors.
        } finally {
            runCatching { retriever.release() }
        }
        return AudioTrack(
            title = title?.takeIf { it.isNotBlank() } ?: fileName,
            uri = document.uri,
            relativePath = document.relativePath,
            artist = artist?.takeIf { it.isNotBlank() },
            album = album?.takeIf { it.isNotBlank() },
            durationMs = durationMs,
            fileSizeBytes = document.sizeBytes
        )
    }

    private companion object {
        const val TAG = "TrackScanner"
        const val METADATA_PARALLELISM = 4
        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )
    }
}
