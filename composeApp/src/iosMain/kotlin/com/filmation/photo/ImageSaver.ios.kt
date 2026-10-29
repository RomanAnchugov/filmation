package com.filmation.photo

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSError
import platform.Foundation.NSMutableData
import platform.Foundation.NSData
import platform.Foundation.appendBytes
import platform.Photos.PHAssetChangeRequest
import platform.Photos.PHPhotoLibrary
import platform.UIKit.UIImage
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Сохранение в галерею iOS через PHPhotoLibrary.
 * Требует NSPhotoLibraryAddUsageDescription в Info.plist (система покажет диалог).
 *
 * withTimeoutOrNull страхует от зависания: если система так и не вызвала completionHandler
 * (редко, но бывает при отмене диалога пермишенов), корутина не останется заблокированной.
 */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun saveJpegToGallery(bytes: ByteArray): Boolean {
    val image = UIImage(data = bytes.toNSData()) ?: return false
    val saved = withTimeoutOrNull(SAVE_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            PHPhotoLibrary.sharedPhotoLibrary().performChanges(
                changeBlock = {
                    PHAssetChangeRequest.creationRequestForAssetFromImage(image)
                },
                completionHandler = { success: Boolean, _: NSError? ->
                    continuation.resume(success)
                },
            )
        }
    }
    return saved == true
}

private const val SAVE_TIMEOUT_MS = 30_000L

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData {
    val ns = NSMutableData()
    if (size == 0) return ns
    usePinned { pinned ->
        ns.appendBytes(pinned.addressOf(0), this@toNSData.size.toULong())
    }
    return ns
}
