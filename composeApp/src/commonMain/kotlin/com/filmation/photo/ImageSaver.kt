package com.filmation.photo

/**
 * Сохранить JPEG-байты в системную галерею.
 * Android — MediaStore (без пермишенов с API 29), iOS — Photos (нужен
 * NSPhotoLibraryAddUsageDescription в Info.plist).
 *
 * @return true при успехе.
 */
expect suspend fun saveJpegToGallery(bytes: ByteArray): Boolean
