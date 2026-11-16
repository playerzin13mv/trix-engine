package com.trix.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Movie
import android.graphics.PorterDuff
import java.io.File
import java.io.FileOutputStream

/** Lê um GIF, descobre os quadros e monta uma spritesheet (ou arquivos soltos) automaticamente. */
object GifSlicer {
    class Result(val frames: Int, val w: Int, val h: Int, val fps: Double, val sheet: String?, val files: List<String>)

    fun isGif(name: String) = name.lowercase().endsWith(".gif")

    /** Atrasos (ms) de cada quadro, lidos dos blocos do arquivo GIF. */
    fun delays(file: File): List<Int> {
        val b = try { file.readBytes() } catch (e: Exception) { return emptyList() }
        if (b.size < 14 || b[0].toInt() != 'G'.code || b[1].toInt() != 'I'.code || b[2].toInt() != 'F'.code) return emptyList()
        fun u(i: Int) = b[i].toInt() and 0xFF
        var p = 13
        val packed = u(10)
        if (packed and 0x80 != 0) p += 3 * (1 shl ((packed and 7) + 1))
        val out = ArrayList<Int>()
        var pending = 100
        fun skipBlocks() {
            while (p < b.size) { val n = u(p); p++; if (n == 0) break; p += n }
        }
        while (p < b.size) {
            when (u(p)) {
                0x21 -> {
                    if (p + 5 < b.size && u(p + 1) == 0xF9) {
                        val d = (u(p + 4) or (u(p + 5) shl 8)) * 10
                        pending = if (d <= 10) 100 else d
                    }
                    p += 2
                    skipBlocks()
                }
                0x2C -> {
                    if (p + 9 >= b.size) break
                    val lp = u(p + 9)
                    p += 10
                    if (lp and 0x80 != 0) p += 3 * (1 shl ((lp and 7) + 1))
                    p++
                    skipBlocks()
                    out.add(pending)
                    pending = 100
                }
                0x3B -> break
                else -> break
            }
        }
        return out
    }

    @Suppress("DEPRECATION")
    fun slice(assetsDir: File, name: String, asFiles: Boolean): Result? {
        val src = File(assetsDir, name)
        val ds = delays(src)
        val movie = try { Movie.decodeFile(src.path) } catch (e: Throwable) { null } ?: return null
        val w = movie.width(); val h = movie.height()
        if (w <= 0 || h <= 0) return null
        val n = Math.max(1, ds.size)
        val dur = movie.duration()
        val base = name.substringBeforeLast('.')

        fun frameBitmap(i: Int): Bitmap {
            var t = 0
            for (k in 0 until i) t += ds.getOrElse(k) { 100 }
            if (dur > 0) movie.setTime(Math.min(t, dur - 1)) else movie.setTime(0)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(0, PorterDuff.Mode.CLEAR)
            movie.draw(c, 0f, 0f)
            return bmp
        }

        val avg = if (ds.isEmpty()) 100.0 else ds.sum().toDouble() / ds.size
        val fps = (1000.0 / avg).coerceIn(1.0, 60.0)

        if (asFiles) {
            val files = ArrayList<String>()
            for (i in 0 until n) {
                val f = "${base}_$i.png"
                FileOutputStream(File(assetsDir, f)).use { frameBitmap(i).compress(Bitmap.CompressFormat.PNG, 100, it) }
                files.add(f)
            }
            return Result(n, w, h, fps, null, files)
        }

        val cols = Math.ceil(Math.sqrt(n.toDouble())).toInt()
        val rows = Math.ceil(n / cols.toDouble()).toInt()
        val sheet = Bitmap.createBitmap(cols * w, rows * h, Bitmap.Config.ARGB_8888)
        val sc = Canvas(sheet)
        for (i in 0 until n) sc.drawBitmap(frameBitmap(i), ((i % cols) * w).toFloat(), ((i / cols) * h).toFloat(), null)
        val sn = "${base}_sheet.png"
        FileOutputStream(File(assetsDir, sn)).use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Result(n, w, h, fps, sn, emptyList())
    }
}
