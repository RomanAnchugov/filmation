package com.filmation.photo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.useContents
import kotlinx.coroutines.launch
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject

/**
 * Системный пикер фото через PHPickerViewController (iOS 14+).
 * Возвращает нормализованные PNG-байты (ориентация применена, <= MAX_SIDE).
 * Нормализация через UIImage покрывает все форматы, включая HEIC.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberPhotoPicker(onPicked: (ByteArray?) -> Unit): () -> Unit {
    val currentOnPicked by rememberUpdatedState(onPicked)
    val mainScope = rememberCoroutineScope()

    return remember {
        {
            val config = PHPickerConfiguration()
            config.filter = PHPickerFilter.imagesFilter
            config.selectionLimit = 1

            val picker = PHPickerViewController(configuration = config)
            picker.delegate = object : NSObject(), PHPickerViewControllerDelegateProtocol {
                override fun picker(
                    picker: PHPickerViewController,
                    didFinishPicking: List<*>,
                ) {
                    picker.dismissViewControllerAnimated(true, completion = null)
                    val result = didFinishPicking.firstOrNull() as? PHPickerResult
                    if (result == null) {
                        mainScope.launch { currentOnPicked(null) }
                        return
                    }
                    val provider = result.itemProvider
                    if (provider.hasItemConformingToTypeIdentifier(UTI_IMAGE)) {
                        // NSData с содержимым файла приходит прямо в completionHandler.
                        provider.loadDataRepresentationForTypeIdentifier(UTI_IMAGE) { data: NSData?, _ ->
                            val image = data?.let { UIImage(data = it) }
                            val bytes = image?.let { normalizeToPng(it, MAX_SIDE) }
                            mainScope.launch { currentOnPicked(bytes) }
                        }
                    } else {
                        mainScope.launch { currentOnPicked(null) }
                    }
                }
            }

            rootViewController()?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

private const val UTI_IMAGE = "public.image"

/**
 * Привести UIImage к PNG: применить ориентацию, ужать длинную сторону до [maxSide].
 */
@OptIn(ExperimentalForeignApi::class)
fun normalizeToPng(src: UIImage, maxSide: Int): ByteArray? {
    val w = src.size.useContents { width }
    val h = src.size.useContents { height }
    val maxDim = maxOf(w, h)
    val scale = if (maxDim > maxSide) maxSide / maxDim else 1.0
    val newW = (w * scale).coerceAtLeast(1.0)
    val newH = (h * scale).coerceAtLeast(1.0)

    val format = UIGraphicsImageRendererFormat.defaultFormat()
    format.scale = 1.0
    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(newW, newH), format = format)
    val data: NSData? = renderer.PNGDataWithActions { _ ->
        src.drawInRect(CGRectMake(0.0, 0.0, newW, newH))
    }
    return data?.toByteArraySafe()
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArraySafe(): ByteArray {
    val size = this.length.toInt()
    if (size == 0) return ByteArray(0)
    val ptr = this.bytes ?: return ByteArray(0)
    @Suppress("UNCHECKED_CAST")
    return (ptr as CPointer<ByteVar>).readBytes(size)
}

/** Корневой UIViewController активной сцены — чтобы презентовать пикер. */
@OptIn(ExperimentalForeignApi::class)
private fun rootViewController(): platform.UIKit.UIViewController? {
    val scene = UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .firstOrNull() ?: return null
    val window = scene.keyWindow
        ?: scene.windows.filterIsInstance<platform.UIKit.UIWindow>().firstOrNull()
        ?: return null
    return window.rootViewController
}
