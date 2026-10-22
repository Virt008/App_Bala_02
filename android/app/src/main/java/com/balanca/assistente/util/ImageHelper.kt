package com.balanca.assistente.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Utilitário responsável pelo armazenamento persistente de imagens no APK Android.
 * Salva as fotos no armazenamento interno privado do aplicativo (context.filesDir/product_images),
 * garantindo que as referências nunca expirem, sobrevivam ao fechamento/reabertura do app
 * e não requeiram permissões perigosas de armazenamento.
 */
object ImageHelper {

    private const val IMAGES_DIR = "product_images"
    private const val MAX_DIMENSION = 400
    private const val JPEG_QUALITY = 85

    /**
     * Obtém o diretório interno para imagens. Cria o diretório se não existir.
     */
    fun getImagesDir(context: Context): File {
        val dir = File(context.filesDir, IMAGES_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Lê uma imagem a partir de uma Uri (da galeria ou seletor), decodifica com redimensionamento
     * proporcional para economizar memória e salva no armazenamento interno privado.
     * Retorna o caminho absoluto do arquivo salvo no disco.
     */
    fun saveImageFromUri(context: Context, uri: Uri, productCode: String): String? {
        return try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (originalBitmap == null) return null

            // Redimensionamento proporcional
            val width = originalBitmap.width
            val height = originalBitmap.height
            val scale = if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
                MAX_DIMENSION.toFloat() / maxOf(width, height).toFloat()
            } else {
                1.0f
            }

            val targetWidth = (width * scale).toInt().coerceAtLeast(1)
            val targetHeight = (height * scale).toInt().coerceAtLeast(1)

            val scaledBitmap = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true)
            } else {
                originalBitmap
            }

            val dir = getImagesDir(context)
            val cleanCode = productCode.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val fileName = "img_${cleanCode}_${System.currentTimeMillis()}.jpg"
            val destFile = File(dir, fileName)

            FileOutputStream(destFile).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.flush()
            }

            if (scaledBitmap != originalBitmap) {
                scaledBitmap.recycle()
            }
            originalBitmap.recycle()

            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Carrega uma miniatura do disco interno de forma segura, com amostragem eficiente.
     */
    fun loadThumbnail(filePath: String?, targetSize: Int = 200): Bitmap? {
        if (filePath.isNullOrBlank()) return null
        return try {
            val file = File(filePath)
            if (!file.exists() || !file.canRead()) return null

            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, boundsOptions)

            var inSampleSize = 1
            if (boundsOptions.outHeight > targetSize || boundsOptions.outWidth > targetSize) {
                val halfHeight = boundsOptions.outHeight / 2
                val halfWidth = boundsOptions.outWidth / 2
                while ((halfHeight / inSampleSize) >= targetSize && (halfWidth / inSampleSize) >= targetSize) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Exclui o arquivo de imagem caso o produto seja excluído ou a imagem seja trocada.
     */
    fun deleteImageFile(filePath: String?) {
        if (filePath.isNullOrBlank()) return
        try {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {}
    }
}
