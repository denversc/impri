# Impri Label Printer App

Impri is an Android application built with Jetpack Compose designed to create and print custom labels via Bluetooth to a Brother PT-P300BT thermal label maker.

## Features

*   **Custom Text Labels:** Print multi-line text labels with automatic font scaling to fit the physical constraints of 12mm thermal tape.
*   **Manual Font Override:** Users can override the auto-scaling and manually specify a font size from 1pt to 60pt.
*   **Alignment Controls:** 
    *   **Vertical Alignment:** Align text to the Top, Center, or Bottom of the 64-pixel printable area.
    *   **Horizontal Alignment:** Align multi-line text to the Left, Center, or Right.
*   **Inverted/Negative Mode:** Print high-contrast labels with white text on a solid black background.
*   **QR Code Generation:** Generate crisp, scannable QR codes.
    *   Place QR codes to the left or right of the text.
    *   Encode the label's text or specify a custom hidden value (like a URL) to be encoded in the QR code while displaying different human-readable text.
    *   QR codes automatically respect the Inverted Color mode.
*   **Print History:** The app saves previously printed labels locally, allowing users to easily tap a past label to load it back into the editor.

## Technical Details for AI Agents

This section contains crucial context for AI agents or developers working on the codebase.

### Hardware Constraints (Brother PT-P300BT)
*   **Tape Size:** The app assumes the use of standard 12mm laminated tape.
*   **Printable Area:** While the printer head is 128 pixels tall (180 DPI), the physical printable area on 12mm tape is only about 9mm high, which equates to exactly **64 pixels** centered vertically. 
*   **Blank Feed Margin:** The physical print head is located approximately 1 inch (25mm) behind the cutting blade. This means every print job has an unavoidable ~1 inch blank feed after the text finishes printing. 

### Core Components

*   **`MainActivity.kt`:** The main entry point. Handles the Jetpack Compose UI layout, the configuration dialogs (Font Size, Alignment, Color Mode, QR Code), and the Bluetooth permission requests necessary for Android 12+.
*   **`MainViewModel.kt`:** Manages the UI state, calculates dynamic font sizes for live previews, and bridges the UI with the printing driver and local database (history).
*   **`LabelBitmapGenerator.kt`:** The core rendering engine. 
    *   Converts user input and settings into a precise 1-bit `Bitmap`.
    *   Uses Android's `StaticLayout` and `TextPaint` to render text.
    *   Implements an auto-shrinking `while` loop to guarantee text never exceeds the 64-pixel maximum height unless manually overridden.
    *   Integrates the `com.google.zxing.qrcode` library for generating 64x64 pixel QR codes.
*   **`BrotherP300BTDriver.kt`:** The Bluetooth communication layer. 
    *   Connects to the printer via the Serial Port Profile (SPP) UUID `00001101-0000-1000-8000-00805F9B34FB`.
    *   Implements the strict Brother CBP-RASTER protocol.
    *   Handles the mandatory status handshake (`ESC i S`) and clearing the 32-byte read buffer.
    *   Sends precise hex commands to configure margins, tape width, page modes, and uncompressed raster mode.
    *   Converts the generated Android `Bitmap` into a 1-bit data stream, utilizing 'G' and 'Z' raster commands.
    *   **Important:** Implements a 5-second coroutine delay after flushing the output stream to prevent the Android OS from aggressively closing the socket before the printer has received the final bytes over the air.

### Libraries
*   **Jetpack Compose:** For the entire UI.
*   **Coroutines:** For asynchronous Bluetooth operations.
*   **ZXing (Zebra Crossing):** `com.google.zxing:core:3.5.3` is used to generate the pixel matrix for the QR codes.
