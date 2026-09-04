package com.example.douyinhelp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 复制抖音链接后自动下载当前视频。
 * 不解析分享链接，直接从当前 Aweme 反射取得视频播放地址。
 */
object DownloadHelper {

    private val registered = AtomicBoolean(false)
    private val downloading = AtomicBoolean(false)
    private var currentAweme: Any? = null

    fun updateCurrentAweme(aweme: Any?) {
        if (aweme != null) currentAweme = aweme
    }

    fun registerClipboardListener(context: Context) {
        if (!registered.compareAndSet(false, true)) return

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            registered.set(false)
            return
        }

        clipboard.addPrimaryClipChangedListener {
            val text = readClipboard(clipboard) ?: return@addPrimaryClipChangedListener
            if (!isDouyinShareText(text)) return@addPrimaryClipChangedListener

            val aweme = currentAweme ?: run {
                toast(context, "未获取到当前视频")
                return@addPrimaryClipChangedListener
            }

            if (!downloading.compareAndSet(false, true)) return@addPrimaryClipChangedListener
            download(context.applicationContext, aweme)
        }
    }

    private fun readClipboard(clipboard: ClipboardManager): String? {
        if (!clipboard.hasPrimaryClip()) return null
        val clip: ClipData = clipboard.primaryClip ?: return null
        return clip.getItemAt(0).coerceToText(null).toString().trim()
    }

    private fun isDouyinShareText(text: String): Boolean {
        if (!text.contains("http", ignoreCase = true)) return false
        return text.contains("v.douyin.com", ignoreCase = true) ||
            text.contains("douyin.com", ignoreCase = true) ||
            text.contains("抖音", ignoreCase = true)
    }

    private fun download(context: Context, aweme: Any) {
        Thread {
            try {
                val url = findVideoUrl(aweme)
                if (url.isNullOrBlank()) {
                    toast(context, "未获取到视频地址")
                    return@Thread
                }

                val tempFile = File.createTempFile("douyin_", ".mp4", context.cacheDir)
                try {
                    downloadToFile(url, tempFile)
                    saveToMediaStore(context, tempFile)
                    toast(context, "视频下载完成")
                } finally {
                    tempFile.delete()
                }
            } catch (e: Throwable) {
                toast(context, "视频下载失败：${e.message ?: "未知错误"}")
            } finally {
                downloading.set(false)
            }
        }.start()
    }

    private fun findVideoUrl(aweme: Any): String? {
        val video = getFieldValue(aweme, "video") ?: return null

        val addresses = listOf("playAddr", "h264PlayAddr", "playAddrH265")
        for (name in addresses) {
            val address = getFieldValue(video, name) ?: continue
            val urls = getFieldValue(address, "urlList") ?: continue
            if (urls is Iterable<*>) {
                urls.forEach { value ->
                    val url = value?.toString()
                    if (!url.isNullOrBlank()) return url
                }
            } else if (urls.javaClass.isArray) {
                for (i in 0 until java.lang.reflect.Array.getLength(urls)) {
                    val url = java.lang.reflect.Array.get(urls, i)?.toString()
                    if (!url.isNullOrBlank()) return url
                }
            }
        }
        return null
    }

    private fun getFieldValue(target: Any, fieldName: String): Any? {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            try {
                val field = type.getDeclaredField(fieldName)
                field.isAccessible = true
                return field.get(target)
            } catch (_: NoSuchFieldException) {
                type = type.superclass
            }
        }
        return null
    }

    private fun downloadToFile(urlString: String, file: File) {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }

        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode}")
            }

            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun saveToMediaStore(context: Context, file: File): Uri? {
        val resolver = context.contentResolver
        val name = "Douyin_${System.currentTimeMillis()}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/DouyinHelp")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("无法创建媒体文件")

        try {
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output, 64 * 1024) }
            } ?: throw IllegalStateException("无法写入媒体文件")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(uri, ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }, null, null)
            }
            return uri
        } catch (e: Throwable) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private fun toast(context: Context, message: String) {
        android.os.Handler(context.mainLooper).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
