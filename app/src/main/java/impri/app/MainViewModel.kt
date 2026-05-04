package impri.app

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

  private val driver = BrotherP300BTDriver()
  private val repository = LabelRepository(application)

  private val _status = MutableStateFlow("Ready to print")
  val status: StateFlow<String> = _status.asStateFlow()

  private val _history = MutableStateFlow<List<Label>>(emptyList())
  val history: StateFlow<List<Label>> = _history.asStateFlow()

  // Label Settings State
  val text = MutableStateFlow("")
  val customFontSize = MutableStateFlow<Float?>(null)
  val verticalAlignment = MutableStateFlow(VerticalAlignment.CENTER)
  val horizontalAlignment = MutableStateFlow(HorizontalAlignment.CENTER)
  val colorMode = MutableStateFlow(ColorMode.NORMAL)
  val qrConfig = MutableStateFlow(QrConfig())

  private val _preview = MutableStateFlow<Bitmap?>(null)
  val preview: StateFlow<Bitmap?> = _preview.asStateFlow()

  init {
    loadHistory()
    setupPreviewGenerator()
  }

  private fun setupPreviewGenerator() {
    combine(text, customFontSize, verticalAlignment, horizontalAlignment, colorMode, qrConfig) {
        args: Array<Any?> ->
        val text = args[0] as String
        val fontSize = args[1] as Float?
        val vAlign = args[2] as VerticalAlignment
        val hAlign = args[3] as HorizontalAlignment
        val color = args[4] as ColorMode
        val qr = args[5] as QrConfig
        PreviewSettings(text, fontSize, vAlign, hAlign, color, qr)
      }
      .debounce(200)
      .onEach { settings -> generatePreview(settings) }
      .launchIn(viewModelScope)
  }

  private fun generatePreview(settings: PreviewSettings) {
    val barcodeChar = LabelBitmapGenerator.BARCODE_CHAR
    val text = settings.text
    val trimmedText = text.trim()
    val count = text.count { it == barcodeChar }
    val cleanText = text.replace(barcodeChar.toString(), "")

    val placement =
      if (count == 0) {
        QrPlacement.NONE
      } else if (
        count == 1 && (trimmedText.startsWith(barcodeChar) || trimmedText.endsWith(barcodeChar))
      ) {
        if (trimmedText.startsWith(barcodeChar)) QrPlacement.LEFT else QrPlacement.RIGHT
      } else {
        QrPlacement.NONE // Invalid placement, preview won't show QR
      }

    val effectiveQrConfig = settings.qrConfig.copy(placement = placement)

    val willPrintQr =
      effectiveQrConfig.placement != QrPlacement.NONE &&
        (if (effectiveQrConfig.useCustomContent) effectiveQrConfig.customContent else cleanText)
          .isNotBlank()

    if (cleanText.isBlank() && !willPrintQr) {
      _preview.value = null
      return
    }

    val (bitmap, _) =
      LabelBitmapGenerator.createLabelBitmap(
        text,
        settings.customFontSize,
        settings.verticalAlignment,
        settings.horizontalAlignment,
        settings.colorMode,
        effectiveQrConfig,
      )

    // The printer head is 128px (~18mm), but the tape is 12mm.
    // 12mm tape covers roughly 86 pixels centered on the print head.
    val tapeHeight = 86
    val tapeTop = (128 - tapeHeight) / 2
    val croppedBitmap = Bitmap.createBitmap(bitmap, 0, tapeTop, bitmap.width, tapeHeight)

    _preview.value = croppedBitmap
  }

  private data class PreviewSettings(
    val text: String,
    val customFontSize: Float?,
    val verticalAlignment: VerticalAlignment,
    val horizontalAlignment: HorizontalAlignment,
    val colorMode: ColorMode,
    val qrConfig: QrConfig,
  )

  private fun loadHistory() {
    _history.value = repository.getLabels()
  }

  fun printLabel(
    text: String,
    fontSize: Float? = null,
    alignment: VerticalAlignment = VerticalAlignment.CENTER,
    horizontalAlignment: HorizontalAlignment = HorizontalAlignment.CENTER,
    colorMode: ColorMode = ColorMode.NORMAL,
    qrConfig: QrConfig = QrConfig(),
  ) {
    val willPrintQr =
      qrConfig.placement != QrPlacement.NONE &&
        (if (qrConfig.useCustomContent) qrConfig.customContent else text).isNotBlank()

    if (text.isBlank() && !willPrintQr) {
      _status.value = "Cannot print empty label"
      return
    }

    viewModelScope.launch {
      _status.value = "Printing..."
      val result =
        driver.printLabel(text, fontSize, alignment, horizontalAlignment, colorMode, qrConfig)

      result.fold(
        onSuccess = {
          _status.value = "Printed successfully!"
          saveLabel(text)
        },
        onFailure = { error -> _status.value = "Print failed: ${error.message}" },
      )
    }
  }

  fun getAutoFontSize(text: String): Float {
    if (text.isEmpty()) return 60f
    val (_, size) =
      LabelBitmapGenerator.createLabelBitmap(text, alignment = VerticalAlignment.CENTER)
    return size
  }

  private fun saveLabel(text: String) {
    repository.saveLabel(text)
    loadHistory()
  }

  fun deleteLabel(id: String) {
    repository.deleteLabel(id)
    loadHistory()
  }
}
