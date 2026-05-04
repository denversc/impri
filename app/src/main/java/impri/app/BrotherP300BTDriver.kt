package impri.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.graphics.Color
import android.util.Log
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BrotherP300BTDriver(private val context: Context) {
  private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
  private val TAG = "BrotherP300BTDriver"

  @SuppressLint("MissingPermission")
  fun findPairedDevice(): BluetoothDevice? {
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val adapter = bluetoothManager.adapter ?: return null
    return adapter.bondedDevices.find { it.name?.contains("P300BT", ignoreCase = true) == true }
  }

  @SuppressLint("MissingPermission")
  suspend fun printLabel(
    text: String,
    customFontSize: Float? = null,
    alignment: VerticalAlignment = VerticalAlignment.CENTER,
    horizontalAlignment: HorizontalAlignment = HorizontalAlignment.CENTER,
    colorMode: ColorMode = ColorMode.NORMAL,
    qrConfig: QrConfig = QrConfig(),
  ): Result<Unit> =
    withContext(Dispatchers.IO) {
      val device =
        findPairedDevice() ?: return@withContext Result.failure(Exception("P300BT not paired"))
      val (bitmap, _) =
        LabelBitmapGenerator.createLabelBitmap(
          text,
          customFontSize,
          alignment,
          horizontalAlignment,
          colorMode,
          qrConfig,
        )

      var socket: BluetoothSocket? = null
      try {
        socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        socket.connect()
        val out = socket.outputStream

        // 1. Initialization
        out.write(ByteArray(200) { 0 }) // 200 nulls to clear
        out.write(byteArrayOf(0x1B, 0x40)) // ESC @ (Initialize)

        // 1b. Handshake (get_status)
        out.write(byteArrayOf(0x1B, 0x69, 0x53)) // ESC i S
        out.flush()

        // Read 32 bytes of status to clear the printer's state machine and allow the job
        val statusBuf = ByteArray(32)
        var bytesRead = 0
        val inputStream = socket.inputStream
        while (bytesRead < 32) {
          val read = inputStream.read(statusBuf, bytesRead, 32 - bytesRead)
          if (read == -1) break
          bytesRead += read
        }

        // 1c. Second Initialization (Python script does this after reading status)
        out.write(ByteArray(200) { 0 })
        out.write(byteArrayOf(0x1B, 0x40))

        // 2. Set to Raster Mode
        out.write(byteArrayOf(0x1B, 0x69, 0x61, 0x01)) // ESC i a 1

        // 3. Set Media & Quality (12mm tape default)
        val rasterLines = bitmap.width
        val n5 = (rasterLines and 0xFF).toByte()
        val n6 = ((rasterLines shr 8) and 0xFF).toByte()
        val n7 = ((rasterLines shr 16) and 0xFF).toByte()
        val n8 = ((rasterLines shr 24) and 0xFF).toByte()

        // ESC i z [flags] [type] [width] [length] [count: 4 bytes] [reserved: 2 bytes]
        // flags: 0xC4 (width | quality | recovery)
        // type: 0x01 (laminated)
        // width: 0x0C (12mm)
        // length: 0x00 (continuous)
        out.write(
          byteArrayOf(0x1B, 0x69, 0x7A, 0xC4.toByte(), 0x01, 0x0C, 0x00, n5, n6, n7, n8, 0x00, 0x00)
        )

        // 3a. Set page mode advanced (ESC i K) - no page chaining (0x08)
        out.write(byteArrayOf(0x1B, 0x69, 0x4B, 0x08))

        // 3b. Set page mode (ESC i M) - no auto cut, no mirror (0x00)
        out.write(byteArrayOf(0x1B, 0x69, 0x4D, 0x00))

        // 3c. Set margin amount (ESC i d) - 28 margin (0x1C)
        out.write(byteArrayOf(0x1B, 0x69, 0x64, 0x1C, 0x00))

        // 3d. Set compression mode to none (M 0)
        out.write(byteArrayOf(0x4D, 0x00))

        // 4. Send Raster Data line-by-line
        for (x in 0 until bitmap.width) {
          val lineData = ByteArray(16)
          var isZeroLine = true
          for (y in 0 until 128) {
            val pixel = bitmap.getPixel(x, y)
            if (pixel != Color.WHITE) {
              val byteIdx = y / 8
              val bitIdx = 7 - (y % 8)
              lineData[byteIdx] = (lineData[byteIdx].toInt() or (1 shl bitIdx)).toByte()
              isZeroLine = false
            }
          }

          if (isZeroLine) {
            out.write(byteArrayOf(0x5A)) // 'Z' command (zerofill)
          } else {
            out.write(
              byteArrayOf(0x47, 0x10, 0x00)
            ) // 'G' command + length (16 bytes, nL=0x10, nH=0x00)
            out.write(lineData)
          }
        }

        // 5. Finalize and Print
        out.write(byteArrayOf(0x1A)) // Control-Z (Print)
        out.flush()

        // Give the printer time to receive and process the data before closing the socket
        kotlinx.coroutines.delay(5000)

        Result.success(Unit)
      } catch (e: IOException) {
        Log.e(TAG, "Print failed", e)
        Result.failure(e)
      } finally {
        socket?.close()
      }
    }
}
