package com.anlin.claude

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray

class MainActivity : Activity() {

    private lateinit var web: WebView
    private val pickRequest = 1001

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = WebViewClient()
        web.webChromeClient = WebChromeClient()
        web.addJavascriptInterface(FileBridge(), "AndroidFS")
        web.loadUrl("file:///android_asset/index.html")
    }

    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    @Deprecated("Uses the platform folder picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != pickRequest || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        contentResolver.takePersistableUriPermission(uri, flags)
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_FOLDER, uri.toString()).apply()
        web.evaluateJavascript("window.onFolderPicked && window.onFolderPicked()", null)
    }

    private fun currentFolder(): DocumentFile? {
        val saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_FOLDER, null) ?: return null
        return DocumentFile.fromTreeUri(this, Uri.parse(saved))
    }

    inner class FileBridge {

        @JavascriptInterface
        fun hasFolder(): Boolean = currentFolder()?.canRead() == true

        @JavascriptInterface
        fun pickFolder() {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
                startActivityForResult(intent, pickRequest)
            }
        }

        @JavascriptInterface
        fun listFiles(): String {
            val arr = JSONArray()
            currentFolder()?.listFiles()?.forEach { f ->
                val name = f.name ?: return@forEach
                if (f.isFile && TEXT_EXT.any { name.endsWith(it) }) arr.put(name)
            }
            return arr.toString()
        }

        @JavascriptInterface
        fun readFile(name: String): String {
            val f = currentFolder()?.findFile(name) ?: return ""
            return contentResolver.openInputStream(f.uri)?.bufferedReader()?.use { it.readText() } ?: ""
        }

        /** Backs up the original as "<name>.bak", then overwrites it. Returns true on success. */
        @JavascriptInterface
        fun writeFile(name: String, content: String): Boolean {
            val dir = currentFolder() ?: return false
            val target = dir.findFile(name) ?: return false
            dir.findFile("$name.bak")?.delete()
            val backup = dir.createFile("application/octet-stream", "$name.bak") ?: return false
            contentResolver.openInputStream(target.uri)?.use { input ->
                contentResolver.openOutputStream(backup.uri)?.use { input.copyTo(it) }
            } ?: return false
            contentResolver.openOutputStream(target.uri, "wt")?.use { it.write(content.toByteArray()) } ?: return false
            return true
        }
    }

    companion object {
        const val APP_URL = "https://anlincn.github.io/claude-mobile/"
        const val PREFS = "fs"
        const val KEY_FOLDER = "folder"
        val TEXT_EXT = listOf(
            ".txt", ".md", ".py", ".js", ".ts", ".json", ".html", ".css", ".java",
            ".kt", ".xml", ".csv", ".log", ".yaml", ".yml", ".sh", ".bat"
        )
    }
}
