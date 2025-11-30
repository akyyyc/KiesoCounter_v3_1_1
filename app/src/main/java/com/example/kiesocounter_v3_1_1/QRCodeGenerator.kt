package com.example.kiesocounter_v3_1_1

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * QR kód generáló utility
 *
 * Használat:
 *   val bitmap = QRCodeGenerator.generateQRCode("ABC123")
 *   Image(bitmap = bitmap.asImageBitmap(), ...)
 */
object QRCodeGenerator {

    /**
     * QR kód generálása szövegből
     *
     * @param text A szöveg amit QR kódba rakunk (pl. "ABC123")
     * @param size QR kód mérete pixelben (alapértelmezett: 512)
     * @return Bitmap QR kóddal, vagy null ha hiba van
     */
    fun generateQRCode(
        text: String,
        size: Int = 512
    ): Bitmap? {
        return try {
            // 1. QR kód beállítások
            val hints = hashMapOf<EncodeHintType, Any>().apply {
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H) // Magas hibatűrés
                put(EncodeHintType.MARGIN, 1) // Kicsi margó
            }

            // 2. QR kód generálása
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size, hints)

            // 3. Bitmap létrehozása
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

            // 4. Pixelek beállítása (fekete/fehér)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(
                        x,
                        y,
                        if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                    )
                }
            }

            bitmap

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * QR kód generálása workspace invite code-ból
     *
     * Példa:
     *   val workspace = FirebaseWorkspace(inviteCode = "ABC123", ...)
     *   val qr = QRCodeGenerator.generateWorkspaceQRCode(workspace)
     */
    fun generateWorkspaceQRCode(
        workspace: FirebaseWorkspace,
        size: Int = 512
    ): Bitmap? {
        // "KIESO:" prefix hogy az app felismerje
        val qrData = "KIESO:${workspace.inviteCode}"
        return generateQRCode(qrData, size)
    }

    /**
     * Beolvasott QR kód feldolgozása
     *
     * Példa:
     *   val code = QRCodeGenerator.parseQRCode("KIESO:ABC123")
     *   // code = "ABC123"
     */
    fun parseQRCode(qrData: String): String? {
        return when {
            // Ha "KIESO:" prefix-el kezdődik
            qrData.startsWith("KIESO:") -> {
                qrData.removePrefix("KIESO:")
            }
            // Ha direkt 6 karakteres kód (pl. "ABC123")
            qrData.matches(Regex("[A-Z0-9]{6}")) -> {
                qrData
            }
            else -> null
        }
    }
}