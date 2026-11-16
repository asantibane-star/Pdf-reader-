package dev.lector.app.data.intake

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Lector mínimo del directorio de un contenedor OLE2 (formato binario de Office 97-2003).
 * Solo lee la cabecera, la FAT y los sectores de directorio para obtener los nombres de flujo;
 * nunca interpreta el contenido de los flujos.
 */
internal object OleDirectory {
    private val SIGNATURE = byteArrayOf(
        0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(),
    )
    private const val HEADER_SIZE = 512
    private const val MAX_REGULAR_SECTOR = 0xFFFFFFFAL
    private const val MAX_DIRECTORY_SECTORS = 4096
    private const val MAX_DIFAT_SECTORS = 512
    private const val DIR_ENTRY_SIZE = 128

    fun hasSignature(head: ByteArray): Boolean =
        head.size >= SIGNATURE.size && SIGNATURE.indices.all { head[it] == SIGNATURE[it] }

    /** Nombres de las entradas del directorio, o null si el contenedor no es válido. */
    fun streamNames(file: File): List<String>? = try {
        RandomAccessFile(file, "r").use { read(it) }
    } catch (_: IOException) {
        null
    } catch (_: IndexOutOfBoundsException) {
        null
    }

    private fun read(raf: RandomAccessFile): List<String>? {
        val header = ByteArray(HEADER_SIZE)
        raf.readFully(header)
        if (!hasSignature(header)) return null
        val sectorShift = u16(header, 30)
        if (sectorShift != 9 && sectorShift != 12) return null
        val sectorSize = 1 shl sectorShift
        val fatSectorCount = u32(header, 44)
        val firstDirSector = u32(header, 48)
        val firstDifat = u32(header, 68)

        val fatSectors = ArrayList<Long>()
        for (i in 0 until 109) {
            val id = u32(header, 76 + 4 * i)
            if (id <= MAX_REGULAR_SECTOR && fatSectors.size < fatSectorCount) fatSectors += id
        }
        var difat = firstDifat
        var guard = 0
        while (fatSectors.size < fatSectorCount && difat <= MAX_REGULAR_SECTOR && guard++ < MAX_DIFAT_SECTORS) {
            val sector = readSector(raf, difat, sectorSize)
            for (i in 0 until sectorSize / 4 - 1) {
                val id = u32(sector, 4 * i)
                if (id <= MAX_REGULAR_SECTOR && fatSectors.size < fatSectorCount) fatSectors += id
            }
            difat = u32(sector, sectorSize - 4)
        }
        if (fatSectors.isEmpty()) return null

        val entriesPerSector = sectorSize / 4
        fun next(sector: Long): Long {
            val fatIndex = (sector / entriesPerSector).toInt()
            if (fatIndex >= fatSectors.size) return -1
            val pos = (fatSectors[fatIndex] + 1) * sectorSize + (sector % entriesPerSector) * 4
            raf.seek(pos)
            val b = ByteArray(4)
            raf.readFully(b)
            return u32(b, 0)
        }

        val names = ArrayList<String>()
        var sector = firstDirSector
        var visited = 0
        while (sector <= MAX_REGULAR_SECTOR && visited++ < MAX_DIRECTORY_SECTORS) {
            val data = readSector(raf, sector, sectorSize)
            for (offset in 0 until sectorSize step DIR_ENTRY_SIZE) {
                val nameLength = u16(data, offset + 64)
                val type = data[offset + 66].toInt()
                if (type != 0 && nameLength in 2..64) {
                    names += String(data, offset, nameLength - 2, Charsets.UTF_16LE)
                }
            }
            sector = next(sector)
            if (sector < 0) break
        }
        return names
    }

    private fun readSector(raf: RandomAccessFile, sector: Long, sectorSize: Int): ByteArray {
        raf.seek((sector + 1) * sectorSize)
        val data = ByteArray(sectorSize)
        raf.readFully(data)
        return data
    }

    private fun u16(b: ByteArray, o: Int): Int = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

    private fun u32(b: ByteArray, o: Int): Long =
        (b[o].toLong() and 0xFF) or ((b[o + 1].toLong() and 0xFF) shl 8) or
            ((b[o + 2].toLong() and 0xFF) shl 16) or ((b[o + 3].toLong() and 0xFF) shl 24)
}
