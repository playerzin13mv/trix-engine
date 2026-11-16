package com.trix.engine

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ImportExport {
    private fun uniqueDir(name: String): File {
        val clean = name.trim().replace(Regex("[^\\p{L}\\p{N} _-]"), "").ifEmpty { "Projeto importado" }
        var d = File(Projects.root(), clean)
        var i = 2
        while (d.exists()) { d = File(Projects.root(), "$clean $i"); i++ }
        return d
    }

    private fun displayName(ctx: Context, uri: Uri): String {
        try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) return c.getString(idx)
            }
        } catch (e: Exception) { }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "projeto"
    }

    /** Importa um .zip (com ou sem pasta raiz) como novo projeto. */
    fun importZip(ctx: Context, uri: Uri): File? {
        val zipName = displayName(ctx, uri).removeSuffix(".zip").removeSuffix(".ZIP")
        val entries = ArrayList<Pair<String, ByteArray?>>()
        try {
            ctx.contentResolver.openInputStream(uri)?.use { raw ->
                ZipInputStream(raw).use { zin ->
                    var e: ZipEntry? = zin.nextEntry
                    while (e != null) {
                        val n = e.name.replace('\\', '/')
                        if (!e.isDirectory && Projects.safeRel(n) && !n.startsWith("__MACOSX")) entries.add(n to zin.readBytes())
                        e = zin.nextEntry
                    }
                }
            }
        } catch (e: Exception) { return null }
        if (entries.isEmpty()) return null

        // se tudo está dentro de uma única pasta, usa a pasta como raiz
        val tops = entries.map { it.first.substringBefore('/', "") }.toSet()
        val strip = if (tops.size == 1 && tops.first().isNotEmpty() && entries.all { it.first.contains('/') }) tops.first() + "/" else ""
        val dir = uniqueDir(if (strip.isNotEmpty()) strip.trimEnd('/') else zipName)
        dir.mkdirs()
        for ((n, data) in entries) {
            val rel = n.removePrefix(strip)
            if (!Projects.safeRel(rel)) continue
            val f = File(dir, rel)
            f.parentFile?.mkdirs()
            f.writeBytes(data ?: ByteArray(0))
        }
        Projects.migrate(dir)
        if (!Projects.isProject(dir)) { dir.deleteRecursively(); return null }
        if (!File(dir, Projects.SETTINGS).exists()) Projects.saveSettings(dir, ProjectSettings())
        return dir
    }

    /** Importa uma pasta escolhida no seletor do Android. */
    fun importTree(ctx: Context, uri: Uri): File? {
        val root = DocumentFile.fromTreeUri(ctx, uri) ?: return null
        val dir = uniqueDir(root.name ?: "Projeto importado")
        dir.mkdirs()
        fun copy(src: DocumentFile, dst: File, depth: Int) {
            if (depth > 8) return
            for (k in src.listFiles()) {
                val nm = k.name ?: continue
                if (!Projects.safeRel(nm) || nm.contains('/')) continue
                if (k.isDirectory) { val d = File(dst, nm); d.mkdirs(); copy(k, d, depth + 1) }
                else ctx.contentResolver.openInputStream(k.uri)?.use { i -> File(dst, nm).outputStream().use { o -> i.copyTo(o) } }
            }
        }
        try { copy(root, dir, 0) } catch (e: Exception) { dir.deleteRecursively(); return null }
        Projects.migrate(dir)
        if (!Projects.isProject(dir)) { dir.deleteRecursively(); return null }
        if (!File(dir, Projects.SETTINGS).exists()) Projects.saveSettings(dir, ProjectSettings())
        return dir
    }

    /** Salva o projeto como TrixEngine/Exports/<nome>.zip */
    fun exportZip(dir: File): File? {
        return try {
            val outDir = File(Projects.root().parentFile, "Exports"); outDir.mkdirs()
            val out = File(outDir, dir.name + ".zip")
            ZipOutputStream(out.outputStream()).use { z ->
                dir.walkTopDown().filter { it.isFile }.forEach { f ->
                    z.putNextEntry(ZipEntry(dir.name + "/" + f.relativeTo(dir).path.replace('\\', '/')))
                    f.inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
            out
        } catch (e: Exception) { null }
    }
}
