package com.denversc.impri.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

  private val driver = BrotherP300BTDriver()
  private val repository = LabelRepository(application)

  private val _status = MutableStateFlow("Ready to print")
  val status: StateFlow<String> = _status.asStateFlow()

  private val _history = MutableStateFlow<List<Label>>(emptyList())
  val history: StateFlow<List<Label>> = _history.asStateFlow()

  init {
    loadHistory()
  }

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
