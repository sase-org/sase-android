package org.sase.mobile.ui.launch

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ImageAttachmentReader {
    suspend fun describe(uri: Uri): ImageAttachmentLoadResult
    suspend fun encodedPayload(
        uri: Uri,
        metadata: ImageAttachmentMetadata,
    ): ImageAttachmentPayloadResult

    fun createCameraCaptureUri(): Uri?
}

class AndroidImageAttachmentReader(
    private val context: Context,
) : ImageAttachmentReader {
    override suspend fun describe(uri: Uri): ImageAttachmentLoadResult = withContext(Dispatchers.IO) {
        try {
            val metadata = context.contentResolver.readMetadata(uri)
            validateMetadata(metadata)?.let { return@withContext ImageAttachmentLoadResult.Failure(it) }
            ImageAttachmentLoadResult.Success(metadata)
        } catch (_: SecurityException) {
            ImageAttachmentLoadResult.Failure(ImageAttachmentError.PermissionDenied)
        } catch (_: FileNotFoundException) {
            ImageAttachmentLoadResult.Failure(ImageAttachmentError.MissingContent)
        } catch (_: IOException) {
            ImageAttachmentLoadResult.Failure(ImageAttachmentError.ReadFailed)
        }
    }

    override suspend fun encodedPayload(
        uri: Uri,
        metadata: ImageAttachmentMetadata,
    ): ImageAttachmentPayloadResult = withContext(Dispatchers.IO) {
        try {
            val validatedMetadata = context.contentResolver.readMetadata(uri, fallback = metadata)
            validateMetadata(validatedMetadata)?.let {
                return@withContext ImageAttachmentPayloadResult.Failure(it)
            }
            val bytes = context.contentResolver.openInputStream(uri)
                ?.use { it.readBytesLimited(MaxImageUploadBytes) }
                ?: return@withContext ImageAttachmentPayloadResult.Failure(ImageAttachmentError.MissingContent)
            if (bytes.size > MaxImageUploadBytes) {
                return@withContext ImageAttachmentPayloadResult.Failure(ImageAttachmentError.Oversize)
            }
            ImageAttachmentPayloadResult.Success(
                ImageAttachmentPayload(
                    metadata = validatedMetadata.copy(byteLength = bytes.size.toLong()),
                    base64Image = Base64.encodeToString(bytes, Base64.NO_WRAP),
                ),
            )
        } catch (_: SecurityException) {
            ImageAttachmentPayloadResult.Failure(ImageAttachmentError.PermissionDenied)
        } catch (_: FileNotFoundException) {
            ImageAttachmentPayloadResult.Failure(ImageAttachmentError.MissingContent)
        } catch (_: ImageOversizeException) {
            ImageAttachmentPayloadResult.Failure(ImageAttachmentError.Oversize)
        } catch (_: IOException) {
            ImageAttachmentPayloadResult.Failure(ImageAttachmentError.ReadFailed)
        }
    }

    override fun createCameraCaptureUri(): Uri? {
        val imageDir = File(context.cacheDir, "launch_images").apply { mkdirs() }
        val file = File.createTempFile("capture_", ".jpg", imageDir)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}

data class ImageAttachmentMetadata(
    val displayName: String,
    val contentType: String,
    val byteLength: Long? = null,
)

data class SelectedImageAttachment(
    val uri: Uri,
    val metadata: ImageAttachmentMetadata,
    val source: ImageAttachmentSource,
)

enum class ImageAttachmentSource {
    Camera,
    Gallery,
}

sealed interface ImageAttachmentLoadResult {
    data class Success(val metadata: ImageAttachmentMetadata) : ImageAttachmentLoadResult
    data class Failure(val error: ImageAttachmentError) : ImageAttachmentLoadResult
}

sealed interface ImageAttachmentPayloadResult {
    data class Success(val payload: ImageAttachmentPayload) : ImageAttachmentPayloadResult
    data class Failure(val error: ImageAttachmentError) : ImageAttachmentPayloadResult
}

data class ImageAttachmentPayload(
    val metadata: ImageAttachmentMetadata,
    val base64Image: String,
)

enum class ImageAttachmentError {
    PermissionDenied,
    MissingContent,
    UnsupportedType,
    Oversize,
    ReadFailed,
}

const val MaxImageUploadBytes: Int = 10 * 1024 * 1024

private val SupportedImageContentTypes = setOf(
    "image/png",
    "image/jpeg",
    "image/webp",
    "image/gif",
)

private fun ContentResolver.readMetadata(
    uri: Uri,
    fallback: ImageAttachmentMetadata? = null,
): ImageAttachmentMetadata {
    val cursorValues = queryOpenableColumns(uri)
    val displayName = cursorValues.displayName
        ?: fallback?.displayName
        ?: uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast('\\')
        ?: "mobile-image"
    val contentType = getType(uri)
        ?: fallback?.contentType
        ?: displayName.inferImageContentType()
        ?: "application/octet-stream"
    val normalizedContentType = contentType.normalizedImageContentType()
    return ImageAttachmentMetadata(
        displayName = displayName.toSafeFilename(normalizedContentType),
        contentType = normalizedContentType,
        byteLength = cursorValues.byteLength ?: fallback?.byteLength,
    )
}

private data class OpenableValues(
    val displayName: String? = null,
    val byteLength: Long? = null,
)

private fun ContentResolver.queryOpenableColumns(uri: Uri): OpenableValues {
    val cursor = query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
        ?: return OpenableValues()
    return cursor.use {
        OpenableValues(
            displayName = it.getStringColumn(OpenableColumns.DISPLAY_NAME),
            byteLength = it.getLongColumn(OpenableColumns.SIZE),
        )
    }
}

private fun Cursor.getStringColumn(columnName: String): String? {
    val index = getColumnIndex(columnName)
    if (index < 0 || !moveToFirst() || isNull(index)) {
        return null
    }
    return getString(index)
}

private fun Cursor.getLongColumn(columnName: String): Long? {
    val index = getColumnIndex(columnName)
    if (index < 0 || !moveToFirst() || isNull(index)) {
        return null
    }
    return getLong(index).takeIf { it >= 0 }
}

private fun validateMetadata(metadata: ImageAttachmentMetadata): ImageAttachmentError? {
    return when {
        metadata.contentType !in SupportedImageContentTypes -> ImageAttachmentError.UnsupportedType
        metadata.byteLength != null && metadata.byteLength > MaxImageUploadBytes -> ImageAttachmentError.Oversize
        else -> null
    }
}

private fun String.inferImageContentType(): String? {
    val extension = substringAfterLast('.', missingDelimiterValue = "")
    if (extension.isBlank()) {
        return null
    }
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
}

private fun String.toSafeFilename(contentType: String): String {
    val name = substringAfterLast('/').substringAfterLast('\\').trim().ifBlank {
        "mobile-image"
    }
    return if ('.' in name) {
        name
    } else {
        "$name.${contentType.defaultExtension()}"
    }
}

private fun String.defaultExtension(): String {
    return when (this) {
        "image/png" -> "png"
        "image/jpeg" -> "jpg"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "img"
    }
}

private fun String.normalizedImageContentType(): String {
    return when (val normalized = lowercase()) {
        "image/jpg" -> "image/jpeg"
        else -> normalized
    }
}

private fun InputStream.readBytesLimited(maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) {
            break
        }
        total += count
        if (total > maxBytes) {
            throw ImageOversizeException()
        }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

private class ImageOversizeException : IOException()
