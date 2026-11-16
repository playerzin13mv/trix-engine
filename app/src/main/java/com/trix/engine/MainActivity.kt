package com.trix.engine

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import java.io.File

class MainActivity : ComponentActivity() {
    private val tick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        // Jogo exportado: o APK carrega o projeto em assets/game e abre direto no jogo.
        val packed = try { assets.list("game")?.let { it.contains("project.json") || it.contains("scene.json") || it.contains("scenes") } == true } catch (e: Exception) { false }
        if (packed) {
            val gameDir = File(filesDir, "game")
            try { copyAssets("game", gameDir) } catch (e: Exception) { }
            setContent { TrixTheme { StandaloneGame(gameDir) { finish() } } }
            return
        }

        setContent {
            TrixTheme {
                val access = remember(tick.intValue) { Projects.hasAccess(this) }
                Root(access) { requestAccess() }
            }
        }
    }

    override fun onResume() { super.onResume(); tick.intValue++ }

    private fun copyAssets(path: String, dest: File) {
        val list = assets.list(path) ?: return
        if (list.isEmpty()) {
            dest.parentFile?.mkdirs()
            assets.open(path).use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
        } else {
            dest.mkdirs()
            for (name in list) copyAssets("$path/$name", File(dest, name))
        }
    }

    private fun requestAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), 1)
        }
    }
}

@Composable
fun StandaloneGame(dir: File, onExit: () -> Unit) {
    val settings = remember { Projects.migrate(dir); Projects.loadSettings(dir) }
    val scene = remember { Projects.loadMainScene(dir, settings).second }
    remember { Images.dir = Projects.assetsDir(dir) }
    PlayScreen(dir, scene, settings, { Projects.readText(dir, it) }, onExit)
}

@Composable
fun Root(hasAccess: Boolean, onRequest: () -> Unit) {
    var open by remember { mutableStateOf<File?>(null) }
    val current = open
    when {
        !hasAccess -> PermissionScreen(onRequest)
        current == null -> ProjectsScreen { open = it }
        else -> EditorScreen(current) { open = null }
    }
}
