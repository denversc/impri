package impri.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import impri.app.ui.theme.ImpriTheme
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {

  private val viewModel: MainViewModel by viewModels()

  private var pendingPrintText: String? = null
  private var pendingFontSize: Float? = null
  private var pendingAlignment: VerticalAlignment = VerticalAlignment.CENTER
  private var pendingHorizontalAlignment: HorizontalAlignment = HorizontalAlignment.CENTER
  private var pendingColorMode: ColorMode = ColorMode.NORMAL
  private var pendingQrConfig: QrConfig = QrConfig()

  private val requestPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
      if (permissions.values.all { it }) {
        pendingPrintText?.let {
          viewModel.printLabel(
            it,
            pendingFontSize,
            pendingAlignment,
            pendingHorizontalAlignment,
            pendingColorMode,
            pendingQrConfig,
          )
          pendingPrintText = null
          pendingFontSize = null
        }
      } else {
        // Handle permission denied
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      ImpriTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          LabelPrinterApp(
            viewModel = viewModel,
            onPrintRequested = { text, fontSize, alignment, horizAlignment, colorMode, qrConfig ->
              checkPermissionsAndPrint(
                text,
                fontSize,
                alignment,
                horizAlignment,
                colorMode,
                qrConfig,
              )
            },
          )
        }
      }
    }
  }

  private fun checkPermissionsAndPrint(
    text: String,
    fontSize: Float?,
    alignment: VerticalAlignment,
    horizAlignment: HorizontalAlignment,
    colorMode: ColorMode,
    qrConfig: QrConfig,
  ) {
    val permissionsNeeded =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
      } else {
        arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
      }

    val missingPermissions = permissionsNeeded.filter {
      ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
    }

    if (missingPermissions.isEmpty()) {
      viewModel.printLabel(text, fontSize, alignment, horizAlignment, colorMode, qrConfig)
    } else {
      pendingPrintText = text
      pendingFontSize = fontSize
      pendingAlignment = alignment
      pendingHorizontalAlignment = horizAlignment
      pendingColorMode = colorMode
      pendingQrConfig = qrConfig
      requestPermissionLauncher.launch(missingPermissions.toTypedArray())
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelPrinterApp(
  viewModel: MainViewModel,
  onPrintRequested:
    (String, Float?, VerticalAlignment, HorizontalAlignment, ColorMode, QrConfig) -> Unit,
) {
  var text by remember { mutableStateOf("") }
  var customFontSize by remember { mutableStateOf<Float?>(null) }
  var verticalAlignment by remember { mutableStateOf(VerticalAlignment.CENTER) }
  var horizontalAlignment by remember { mutableStateOf(HorizontalAlignment.CENTER) }
  var colorMode by remember { mutableStateOf(ColorMode.NORMAL) }
  var qrConfig by remember { mutableStateOf(QrConfig()) }

  var showFontSizeDialog by remember { mutableStateOf(false) }
  var showAlignmentDialog by remember { mutableStateOf(false) }
  var showHorizontalAlignmentDialog by remember { mutableStateOf(false) }
  var showColorModeDialog by remember { mutableStateOf(false) }
  var showQrCodeDialog by remember { mutableStateOf(false) }
  var showPlacementErrorDialog by remember { mutableStateOf(false) }
  var showDateDialog by remember { mutableStateOf(false) }

  var pendingPrintText by remember { mutableStateOf("") }

  val status by viewModel.status.collectAsState()
  val history by viewModel.history.collectAsState()

  val autoFontSize = remember(text) { viewModel.getAutoFontSize(text) }
  val currentFontSize = customFontSize ?: autoFontSize

  if (showFontSizeDialog) {
    FontSizeDialog(
      initialSize = currentFontSize,
      autoSize = autoFontSize,
      initialIsAuto = customFontSize == null,
      onDismiss = { showFontSizeDialog = false },
      onConfirm = { size ->
        customFontSize = size
        showFontSizeDialog = false
      },
    )
  }

  if (showAlignmentDialog) {
    VerticalAlignmentDialog(
      currentAlignment = verticalAlignment,
      onDismiss = { showAlignmentDialog = false },
      onAlignmentSelected = { alignment ->
        verticalAlignment = alignment
        showAlignmentDialog = false
      },
    )
  }

  if (showHorizontalAlignmentDialog) {
    HorizontalAlignmentDialog(
      currentAlignment = horizontalAlignment,
      onDismiss = { showHorizontalAlignmentDialog = false },
      onAlignmentSelected = { alignment ->
        horizontalAlignment = alignment
        showHorizontalAlignmentDialog = false
      },
    )
  }

  if (showColorModeDialog) {
    ColorModeDialog(
      currentMode = colorMode,
      onDismiss = { showColorModeDialog = false },
      onModeSelected = { mode ->
        colorMode = mode
        showColorModeDialog = false
      },
    )
  }

  if (showQrCodeDialog) {
    AddQrCodeDialog(
      currentConfig = qrConfig,
      onDismiss = { showQrCodeDialog = false },
      onConfirm = { config ->
        qrConfig = config
        text += LabelBitmapGenerator.BARCODE_CHAR
        showQrCodeDialog = false
      },
    )
  }

  if (showDateDialog) {
    DateDialog(
      onDismiss = { showDateDialog = false },
      onDateSelected = { selectedDate ->
        text += selectedDate
        showDateDialog = false
      },
    )
  }

  if (showPlacementErrorDialog) {
    BarcodePlacementErrorDialog(
      onDismiss = { showPlacementErrorDialog = false },
      onSelectPlacement = { placement ->
        onPrintRequested(
          pendingPrintText,
          customFontSize,
          verticalAlignment,
          horizontalAlignment,
          colorMode,
          qrConfig.copy(placement = placement),
        )
        showPlacementErrorDialog = false
      },
    )
  }

  Column(
    modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    OutlinedTextField(
      value = text,
      onValueChange = { text = it },
      label = { Text("Label Text") },
      modifier = Modifier.fillMaxWidth(),
      maxLines = 3,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Button(
      onClick = {
        val barcodeChar = LabelBitmapGenerator.BARCODE_CHAR
        val count = text.count { it == barcodeChar }
        val cleanText = text.replace(Regex("\\s*$barcodeChar\\s*"), "")
        val trimmedText = text.trim()

        if (count == 0) {
          onPrintRequested(
            text,
            customFontSize,
            verticalAlignment,
            horizontalAlignment,
            colorMode,
            qrConfig.copy(placement = QrPlacement.NONE),
          )
        } else if (
          count == 1 && (trimmedText.startsWith(barcodeChar) || trimmedText.endsWith(barcodeChar))
        ) {
          val placement =
            if (trimmedText.startsWith(barcodeChar)) QrPlacement.LEFT else QrPlacement.RIGHT
          onPrintRequested(
            cleanText,
            customFontSize,
            verticalAlignment,
            horizontalAlignment,
            colorMode,
            qrConfig.copy(placement = placement),
          )
        } else {
          pendingPrintText = cleanText
          showPlacementErrorDialog = true
        }
      },
      modifier = Modifier.fillMaxWidth(),
      enabled = text.isNotBlank(),
    ) {
      Text("Print Label")
    }

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedButton(onClick = { showFontSizeDialog = true }, modifier = Modifier.fillMaxWidth()) {
      Icon(Icons.Default.TextFormat, contentDescription = null)
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        "Font Size: ${currentFontSize.toInt()} pt${if (customFontSize == null) " (Auto)" else ""}"
      )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
      OutlinedIconButton(
        onClick = { showAlignmentDialog = true },
        modifier = Modifier.size(48.dp),
      ) {
        val icon =
          when (verticalAlignment) {
            VerticalAlignment.TOP -> Icons.Default.VerticalAlignTop
            VerticalAlignment.CENTER -> Icons.Default.VerticalAlignCenter
            VerticalAlignment.BOTTOM -> Icons.Default.VerticalAlignBottom
          }
        Icon(icon, contentDescription = "Vertical Alignment")
      }

      Spacer(modifier = Modifier.width(16.dp))

      OutlinedIconButton(
        onClick = { showHorizontalAlignmentDialog = true },
        modifier = Modifier.size(48.dp),
      ) {
        val icon =
          when (horizontalAlignment) {
            HorizontalAlignment.LEFT -> Icons.Default.FormatAlignLeft
            HorizontalAlignment.CENTER -> Icons.Default.FormatAlignCenter
            HorizontalAlignment.RIGHT -> Icons.Default.FormatAlignRight
          }
        Icon(icon, contentDescription = "Horizontal Alignment")
      }

      Spacer(modifier = Modifier.width(16.dp))

      OutlinedIconButton(
        onClick = { showColorModeDialog = true },
        modifier = Modifier.size(48.dp),
      ) {
        val icon =
          when (colorMode) {
            ColorMode.NORMAL -> Icons.Default.FormatColorText
            ColorMode.INVERTED -> Icons.Default.InvertColors
          }
        Icon(icon, contentDescription = "Color Mode")
      }

      Spacer(modifier = Modifier.width(16.dp))

      OutlinedIconButton(onClick = { showQrCodeDialog = true }, modifier = Modifier.size(48.dp)) {
        Icon(Icons.Default.QrCode, contentDescription = "Add QR Code")
      }

      Spacer(modifier = Modifier.width(16.dp))

      OutlinedIconButton(onClick = { showDateDialog = true }, modifier = Modifier.size(48.dp)) {
        Icon(Icons.Default.CalendarToday, contentDescription = "Add Date")
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = status,
      style = MaterialTheme.typography.bodyMedium,
      color =
        if (status.contains("failed", ignoreCase = true)) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurface,
    )

    Spacer(modifier = Modifier.height(24.dp))

    HorizontalDivider()

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = "History",
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.align(Alignment.Start),
    )

    Spacer(modifier = Modifier.height(8.dp))

    LazyColumn(modifier = Modifier.fillMaxSize()) {
      items(history, key = { it.id }) { label ->
        HistoryItem(
          label = label,
          onClick = { text = label.text },
          onDelete = { viewModel.deleteLabel(label.id) },
        )
      }
    }
  }
}

@Composable
fun DateDialog(onDismiss: () -> Unit, onDateSelected: (String) -> Unit) {
  val now = ZonedDateTime.now()
  val formatter1 = DateTimeFormatter.ofPattern("EEE MMM d")
  val formatter2 = DateTimeFormatter.ofPattern("MMM d, yyyy")

  val date1 = now.format(formatter1)
  val date2 = now.format(formatter2)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Select Date Format") },
    text = {
      Column {
        DateOption(label = date1, onClick = { onDateSelected(date1) })
        DateOption(label = date2, onClick = { onDateSelected(date2) })
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
fun DateOption(label: String, onClick: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text = label, style = MaterialTheme.typography.bodyLarge)
  }
}

@Composable
fun VerticalAlignmentDialog(
  currentAlignment: VerticalAlignment,
  onDismiss: () -> Unit,
  onAlignmentSelected: (VerticalAlignment) -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Vertical Alignment") },
    text = {
      Column {
        AlignmentOption(
          label = "Top",
          icon = Icons.Default.VerticalAlignTop,
          isSelected = currentAlignment == VerticalAlignment.TOP,
          onClick = { onAlignmentSelected(VerticalAlignment.TOP) },
        )
        AlignmentOption(
          label = "Center",
          icon = Icons.Default.VerticalAlignCenter,
          isSelected = currentAlignment == VerticalAlignment.CENTER,
          onClick = { onAlignmentSelected(VerticalAlignment.CENTER) },
        )
        AlignmentOption(
          label = "Bottom",
          icon = Icons.Default.VerticalAlignBottom,
          isSelected = currentAlignment == VerticalAlignment.BOTTOM,
          onClick = { onAlignmentSelected(VerticalAlignment.BOTTOM) },
        )
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
fun HorizontalAlignmentDialog(
  currentAlignment: HorizontalAlignment,
  onDismiss: () -> Unit,
  onAlignmentSelected: (HorizontalAlignment) -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Horizontal Alignment") },
    text = {
      Column {
        AlignmentOption(
          label = "Left",
          icon = Icons.Default.FormatAlignLeft,
          isSelected = currentAlignment == HorizontalAlignment.LEFT,
          onClick = { onAlignmentSelected(HorizontalAlignment.LEFT) },
        )
        AlignmentOption(
          label = "Center",
          icon = Icons.Default.FormatAlignCenter,
          isSelected = currentAlignment == HorizontalAlignment.CENTER,
          onClick = { onAlignmentSelected(HorizontalAlignment.CENTER) },
        )
        AlignmentOption(
          label = "Right",
          icon = Icons.Default.FormatAlignRight,
          isSelected = currentAlignment == HorizontalAlignment.RIGHT,
          onClick = { onAlignmentSelected(HorizontalAlignment.RIGHT) },
        )
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
fun AddQrCodeDialog(currentConfig: QrConfig, onDismiss: () -> Unit, onConfirm: (QrConfig) -> Unit) {
  var useCustomContent by remember { mutableStateOf(currentConfig.useCustomContent) }
  var customContent by remember { mutableStateOf(currentConfig.customContent) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add QR Code") },
    text = {
      Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = !useCustomContent, onCheckedChange = { useCustomContent = !it })
          Text(
            text = "Use Label Text",
            modifier = Modifier.clickable { useCustomContent = !useCustomContent },
          )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
          value = customContent,
          onValueChange = {
            customContent = it
            useCustomContent = true
          },
          label = { Text("Custom Value") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = { onConfirm(QrConfig(QrPlacement.NONE, useCustomContent, customContent)) }
      ) {
        Text("Add")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
fun BarcodePlacementErrorDialog(onDismiss: () -> Unit, onSelectPlacement: (QrPlacement) -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("QR Code Placement") },
    text = {
      Text(
        "The QR Code marker is missing, in an invalid location, or occurs multiple times.\n\n" +
          "Would you like to print the QR Code to the left or right of the text?"
      )
    },
    confirmButton = {
      Row {
        TextButton(onClick = { onSelectPlacement(QrPlacement.LEFT) }) { Text("Left") }
        TextButton(onClick = { onSelectPlacement(QrPlacement.RIGHT) }) { Text("Right") }
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
fun ColorModeDialog(
  currentMode: ColorMode,
  onDismiss: () -> Unit,
  onModeSelected: (ColorMode) -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Color Mode") },
    text = {
      Column {
        AlignmentOption(
          label = "Normal (Black Text)",
          icon = Icons.Default.FormatColorText,
          isSelected = currentMode == ColorMode.NORMAL,
          onClick = { onModeSelected(ColorMode.NORMAL) },
        )
        AlignmentOption(
          label = "Inverted (White Text)",
          icon = Icons.Default.InvertColors,
          isSelected = currentMode == ColorMode.INVERTED,
          onClick = { onModeSelected(ColorMode.INVERTED) },
        )
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
fun AlignmentOption(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  isSelected: Boolean,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      icon,
      contentDescription = null,
      tint =
        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.width(16.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.bodyLarge,
      color =
        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
    )
  }
}

@Composable
fun FontSizeDialog(
  initialSize: Float,
  autoSize: Float,
  initialIsAuto: Boolean,
  onDismiss: () -> Unit,
  onConfirm: (Float?) -> Unit,
) {
  var sliderValue by remember { mutableStateOf(initialSize) }
  var isAuto by remember { mutableStateOf(initialIsAuto) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Select Font Size") },
    text = {
      Column {
        Text(
          if (isAuto) "Size: ${sliderValue.toInt()} pt (Auto)"
          else "Size: ${sliderValue.toInt()} pt"
        )
        Slider(
          value = sliderValue,
          onValueChange = {
            sliderValue = it
            isAuto = false
          },
          valueRange = 1f..60f,
          steps = 58,
        )
        TextButton(
          onClick = {
            sliderValue = autoSize
            isAuto = true
          },
          modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
          Text("Reset to Auto-calculated")
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(if (isAuto) null else sliderValue) }) { Text("Set") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
fun HistoryItem(label: Label, onClick: () -> Unit, onDelete: () -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = label.text,
        modifier = Modifier.weight(1f),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyLarge,
      )
      IconButton(onClick = onDelete) {
        Icon(
          imageVector = Icons.Default.Delete,
          contentDescription = "Delete",
          tint = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}
