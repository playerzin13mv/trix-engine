package com.trix.engine

/** Paleta de blocos do Tilemap. Id 0 = vazio. */
object TileSet {
    val names = listOf("Vazio", "Grama", "Terra", "Pedra", "Madeira", "Folhas", "Tijolo", "Bloco ?", "Areia", "Água", "Ouro")
    val colors = intArrayOf(
        0x00000000, 0xFF5DA130.toInt(), 0xFF8B5A2B.toInt(), 0xFF8A8F98.toInt(), 0xFFA0522D.toInt(),
        0xFF2E7D32.toInt(), 0xFFB5472A.toInt(), 0xFFF6C02B.toInt(), 0xFFE0C97B.toInt(), 0xFF3B82F6.toInt(), 0xFFFACC15.toInt()
    )
    val count get() = names.size
    fun solid(id: Int) = (id in 1..8) || id == 10
    fun name(id: Int) = names.getOrElse(id) { "?" }
}
