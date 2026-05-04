package impri.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

enum class VerticalAlignment {
  TOP,
  CENTER,
  BOTTOM,
}

enum class HorizontalAlignment {
  LEFT,
  CENTER,
  RIGHT,
}

enum class ColorMode {
  NORMAL,
  INVERTED,
}

enum class QrPlacement {
  NONE,
  LEFT,
  RIGHT,
}

data class QrConfig(
  val placement: QrPlacement = QrPlacement.NONE,
  val useCustomContent: Boolean = false,
  val customContent: String = "",
)

object LabelBitmapGenerator {
  const val BARCODE_CHAR = '\u25A3'
  private const val PRINTER_HEAD_WIDTH = 128 // Height of the bitmap for printing

  fun createLabelBitmap(
    text: String,
    customFontSize: Float? = null,
    alignment: VerticalAlignment = VerticalAlignment.CENTER,
    horizontalAlignment: HorizontalAlignment = HorizontalAlignment.CENTER,
    colorMode: ColorMode = ColorMode.NORMAL,
    qrConfig: QrConfig = QrConfig(),
  ): Pair<Bitmap, Float> {
    val cleanText = text.replace(Regex("\\s*$BARCODE_CHAR\\s*"), "")
    // A 12mm tape only has a printable area of about 9mm (roughly 64 pixels)
    // centered on the 128-pixel print head.
    val maxPrintHeight = 64f
    var currentFontSize = customFontSize ?: 60f

    val textColor = if (colorMode == ColorMode.INVERTED) Color.WHITE else Color.BLACK
    val backgroundColor = if (colorMode == ColorMode.INVERTED) Color.BLACK else Color.WHITE

    val textPaint =
      TextPaint().apply {
        color = textColor
        typeface = Typeface.DEFAULT
        isAntiAlias = false
      }

    val spannableText = android.text.SpannableString(cleanText)
    val firstLineEnd = cleanText.indexOf('\n').let { if (it == -1) cleanText.length else it }
    if (firstLineEnd > 0) {
      spannableText.setSpan(
        android.text.style.StyleSpan(Typeface.BOLD),
        0,
        firstLineEnd,
        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
      )
    }

    // Function to calculate layout for a given font size
    fun calculateLayout(fontSize: Float): StaticLayout {
      textPaint.textSize = fontSize
      var maxWidth = 0f
      cleanText.split("\n").forEachIndexed { index, line ->
        val w =
          if (index == 0) {
            textPaint.typeface = Typeface.DEFAULT_BOLD
            val width = textPaint.measureText(line)
            textPaint.typeface = Typeface.DEFAULT
            width
          } else {
            textPaint.measureText(line)
          }
        if (w > maxWidth) maxWidth = w
      }
      val exactWidth = maxWidth.toInt().coerceAtLeast(1)

      val textAlignment =
        when (horizontalAlignment) {
          HorizontalAlignment.LEFT -> Layout.Alignment.ALIGN_NORMAL
          HorizontalAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
          HorizontalAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }

      return StaticLayout.Builder.obtain(
          spannableText,
          0,
          spannableText.length,
          textPaint,
          exactWidth,
        )
        .setAlignment(textAlignment)
        .setLineSpacing(0f, 1f)
        .setIncludePad(false)
        .build()
    }

    // Shrink font size until the total height fits within the print head
    // Only auto-shrink if no custom font size is provided
    var layout = calculateLayout(currentFontSize)
    if (customFontSize == null) {
      while (layout.height > maxPrintHeight && currentFontSize > 1f) {
        currentFontSize -= 1f
        layout = calculateLayout(currentFontSize)
      }
    }

    // Prepare QR Code if enabled
    var qrBitmap: Bitmap? = null
    val qrSize = 64
    val qrPadding = 16
    if (qrConfig.placement != QrPlacement.NONE) {
      val qrContent = if (qrConfig.useCustomContent) qrConfig.customContent else cleanText
      if (qrContent.isNotBlank()) {
        try {
          val writer = QRCodeWriter()
          val hints = mapOf(com.google.zxing.EncodeHintType.MARGIN to 0)
          // Request the minimum possible size (0,0) to get the raw module grid
          val bitMatrix = writer.encode(qrContent, BarcodeFormat.QR_CODE, 0, 0, hints)
          val mWidth = bitMatrix.width
          val mHeight = bitMatrix.height

          qrBitmap = Bitmap.createBitmap(qrSize, qrSize, Bitmap.Config.RGB_565)
          // Manually stretch the raw grid to exactly qrSize x qrSize (64x64)
          // using nearest-neighbor mapping.
          for (x in 0 until qrSize) {
            for (y in 0 until qrSize) {
              val matrixX = x * mWidth / qrSize
              val matrixY = y * mHeight / qrSize
              qrBitmap.setPixel(
                x,
                y,
                if (bitMatrix[matrixX, matrixY]) textColor else backgroundColor,
              )
            }
          }
        } catch (e: Exception) {
          // Fail silently
        }
      }
    }

    var totalWidth = layout.width.coerceAtLeast(1)
    if (qrBitmap != null) {
      totalWidth += qrSize + qrPadding
    }

    val height = PRINTER_HEAD_WIDTH

    val bitmap = Bitmap.createBitmap(totalWidth, height, Bitmap.Config.RGB_565)
    val canvas = Canvas(bitmap)
    canvas.drawColor(backgroundColor)

    val printableTop = (PRINTER_HEAD_WIDTH - maxPrintHeight) / 2f

    var textXOffset = 0f
    var qrXOffset = 0f

    if (qrBitmap != null) {
      if (qrConfig.placement == QrPlacement.LEFT) {
        qrXOffset = 0f
        textXOffset = (qrSize + qrPadding).toFloat()
      } else {
        qrXOffset = (layout.width + qrPadding).toFloat()
        textXOffset = 0f
      }

      canvas.drawBitmap(qrBitmap, qrXOffset, printableTop, null)
    }

    val yOffset =
      when (alignment) {
        VerticalAlignment.TOP -> printableTop
        VerticalAlignment.CENTER -> printableTop + (maxPrintHeight - layout.height) / 2f
        VerticalAlignment.BOTTOM -> printableTop + (maxPrintHeight - layout.height)
      }

    canvas.save()
    canvas.translate(textXOffset, yOffset)
    layout.draw(canvas)
    canvas.restore()

    return Pair(bitmap, currentFontSize)
  }
}
