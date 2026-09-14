package com.atip.fourtif

import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.patcherTab).setOnClickListener { show(PatcherFragment()) }
        findViewById<Button>(R.id.tiktokTab).setOnClickListener { show(TikTokFragment()) }
        if (savedInstanceState == null) show(PatcherFragment())
    }

    private fun show(fragment: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.content, fragment).commit()
    }
}

class PatcherFragment : Fragment() {
    private var source: Uri? = null
    private var restoreMode = false
    private lateinit var selectButton: Button
    private lateinit var actionButton: Button
    private lateinit var restoreButton: Button
    private lateinit var fileName: TextView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var fpsGroup: RadioGroup

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.fragment_patcher, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        selectButton = view.findViewById(R.id.selectVideo)
        actionButton = view.findViewById(R.id.patchVideo)
        restoreButton = view.findViewById(R.id.restoreVideo)
        fileName = view.findViewById(R.id.fileName)
        status = view.findViewById(R.id.status)
        progress = view.findViewById(R.id.progress)
        fpsGroup = view.findViewById(R.id.fpsGroup)

        selectButton.setOnClickListener { chooseVideo() }
        actionButton.setOnClickListener { processVideo() }
        restoreButton.setOnClickListener {
            restoreMode = true
            source = null
            fileName.text = "No video selected"
            selectButton.text = "SELECT PATCHED VIDEO"
            actionButton.text = "RESTORE VIDEO"
            actionButton.isEnabled = false
            status.text = "Select a 4tif-patched video, then choose its original FPS setting."
        }
    }

    private fun chooseVideo() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "video/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, PICK_VIDEO)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_VIDEO || resultCode != AppCompatActivity.RESULT_OK) return
        source = data?.data ?: return
        try { requireContext().contentResolver.takePersistableUriPermission(source!!, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) {}
        fileName.text = displayName(source!!)
        actionButton.isEnabled = true
        status.text = if (restoreMode) "Ready to restore." else "Ready to patch."
    }

    private fun processVideo() {
        val uri = source ?: return
        val factor = if (fpsGroup.checkedRadioButtonId == R.id.fps120) 6 else 2
        actionButton.isEnabled = false
        progress.visibility = View.VISIBLE
        status.text = if (restoreMode) "Restoring video…" else "Patching video…"

        Executors.newSingleThreadExecutor().execute {
            try {
                val bytes = requireContext().contentResolver.openInputStream(uri)?.use { input ->
                    val output = ByteArrayOutputStream()
                    input.copyTo(output)
                    output.toByteArray()
                } ?: error("Could not read the selected file.")
                val changed = Mp4Timing.patch(bytes, factor, restoreMode)
                if (changed == 0) error("No compatible MP4 timing metadata was found.")
                val savedName = saveOutput(bytes, displayName(uri), restoreMode)
                activity?.runOnUiThread {
                    progress.visibility = View.GONE
                    actionButton.isEnabled = true
                    status.text = "Done — saved to Downloads as $savedName"
                    if (restoreMode) resetRestoreMode()
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    progress.visibility = View.GONE
                    actionButton.isEnabled = true
                    status.text = "Error: ${e.message ?: "Could not process this video."}"
                }
            }
        }
    }

    private fun resetRestoreMode() {
        restoreMode = false
        source = null
        fileName.text = "No video selected"
        selectButton.text = "SELECT VIDEO"
        actionButton.text = "PATCH VIDEO"
        actionButton.isEnabled = false
    }

    private fun saveOutput(bytes: ByteArray, originalName: String, restoring: Boolean): String {
        val extension = originalName.substringAfterLast('.', "mp4")
        val rawBase = originalName.substringBeforeLast('.', originalName)
        val base = if (restoring) rawBase.removeSuffix("_4tif") else rawBase
        val name = if (restoring) "${base}_restored.$extension" else "${base}_4tif.$extension"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, if (extension.equals("mov", true)) "video/quicktime" else "video/mp4")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = requireContext().contentResolver
        val outputUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Could not create output file.")
        resolver.openOutputStream(outputUri)?.use { it.write(bytes) } ?: error("Could not write output file.")
        values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(outputUri, values, null, null)
        return name
    }

    private fun displayName(uri: Uri): String {
        var name = "video.mp4"
        val cursor: Cursor? = requireContext().contentResolver.query(uri, null, null, null, null)
        cursor?.use { if (it.moveToFirst()) name = it.getString(it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)) }
        return name
    }

    companion object { private const val PICK_VIDEO = 41 }
}

class TikTokFragment : Fragment() {
    private var chooser: ValueCallback<Array<Uri>>? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        return WebView(requireContext()).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = WebViewClient()
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(view: WebView?, callback: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean {
                    chooser?.onReceiveValue(null)
                    chooser = callback
                    startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "video/*"; addCategory(Intent.CATEGORY_OPENABLE)
                    }, PICK_UPLOAD)
                    return true
                }
            }
            loadUrl("https://www.tiktok.com/upload")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_UPLOAD) chooser?.onReceiveValue(data?.data?.let { arrayOf(it) })
        chooser = null
    }
    companion object { private const val PICK_UPLOAD = 42 }
}

object Mp4Timing {
    private val containers = setOf("moov", "trak", "mdia", "minf", "stbl", "edts", "udta", "meta", "dinf")

    fun patch(data: ByteArray, factor: Int, undo: Boolean): Int {
        var changed = 0
        fun u32(offset: Int): Long = ((data[offset].toLong() and 255) shl 24) or ((data[offset + 1].toLong() and 255) shl 16) or ((data[offset + 2].toLong() and 255) shl 8) or (data[offset + 3].toLong() and 255)
        fun type(offset: Int) = String(data, offset + 4, 4, StandardCharsets.ISO_8859_1)
        fun writeU32(offset: Int, value: Long) { data[offset] = (value shr 24).toByte(); data[offset + 1] = (value shr 16).toByte(); data[offset + 2] = (value shr 8).toByte(); data[offset + 3] = value.toByte() }
        fun adjust(dataStart: Int, end: Int) {
            if (dataStart + 4 > end) return
            val version = data[dataStart].toInt() and 255
            val offset = dataStart + if (version == 1) 20 else 12
            if (offset + 4 > end) return
            val old = u32(offset)
            val next = if (undo) old * factor else old / factor
            if (next in 1..0xFFFFFFFFL) { writeU32(offset, next); changed++ }
        }
        fun walk(start: Int, end: Int) {
            var offset = start
            while (offset + 8 <= end) {
                var size = u32(offset)
                val boxType = type(offset)
                var header = 8
                if (size == 1L) {
                    if (offset + 16 > end) break
                    size = (u32(offset + 8) shl 32) or u32(offset + 12)
                    header = 16
                }
                if (size == 0L) size = (end - offset).toLong()
                if (size < header || size > (end - offset).toLong() || size > Int.MAX_VALUE) break
                val boxEnd = offset + size.toInt()
                val content = offset + header
                if (boxType == "mvhd" || boxType == "mdhd") adjust(content, boxEnd)
                if (boxType in containers) walk(content + if (boxType == "meta") 4 else 0, boxEnd)
                offset = boxEnd
            }
        }
        walk(0, data.size)
        return changed
    }
}
