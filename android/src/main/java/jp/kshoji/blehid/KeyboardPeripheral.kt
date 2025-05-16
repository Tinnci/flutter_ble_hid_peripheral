package jp.kshoji.blehid

import android.content.Context
import android.util.Log
import androidx.annotation.RequiresApi
import android.os.Build

/**
 * BLE Keyboard (US layout)
 *
 * @author K.Shoji
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class KeyboardPeripheral(context: Context) : HidPeripheral(
    context,
    needInputReport = true, // Keyboard sends input reports
    needOutputReport = true, // Keyboard can receive output reports (LED status)
    needFeatureReport = false, // Typically no feature reports for basic keyboard
    dataSendingRate = 20 // Milliseconds, adjust as needed
) {

    companion object {
        private val TAG = KeyboardPeripheral::class.java.simpleName

        const val MODIFIER_KEY_NONE: Byte = 0
        const val MODIFIER_KEY_CTRL: Byte = 1
        const val MODIFIER_KEY_SHIFT: Byte = 2
        const val MODIFIER_KEY_ALT: Byte = 4
        // Add other modifiers if needed (GUI/Command, Right Ctrl, etc.)
        // const val MODIFIER_KEY_GUI: Byte = 8 // Example for Windows/Command key

        // Standard Key Codes (many omitted for brevity, use the original for completeness)
        const val KEY_A: Byte = 0x04
        const val KEY_B: Byte = 0x05
        // ...
        const val KEY_Z: Byte = 0x1d
        const val KEY_1: Byte = 0x1e
        // ...
        const val KEY_0: Byte = 0x27
        const val KEY_ENTER: Byte = 0x28     // Keyboard Return (ENTER)
        const val KEY_ESC: Byte = 0x29       // Keyboard ESCAPE
        const val KEY_BACKSPACE: Byte = 0x2a // Keyboard DELETE (Backspace)
        const val KEY_TAB: Byte = 0x2b       // Keyboard Tab
        const val KEY_SPACE: Byte = 0x2c     // Keyboard Spacebar
        // ... F keys, arrows, etc. ...
        const val KEY_F1: Byte = 0x3a
        const val KEY_RIGHT_ARROW: Byte = 0x4f
        const val KEY_LEFT_ARROW: Byte = 0x50
        const val KEY_DOWN_ARROW: Byte = 0x51
        const val KEY_UP_ARROW: Byte = 0x52


        // Report Map for a standard 104-key keyboard
        // This defines what the keyboard is capable of sending
        private val REPORT_MAP = byteArrayOf(
            USAGE_PAGE(1),      0x01,       // Generic Desktop
            USAGE(1),           0x06,       // Keyboard
            COLLECTION(1),      0x01,       // Application
            REPORT_ID(1),       0x01,       // Report ID (1)
            USAGE_PAGE(1),      0x07,       // Key Codes
            USAGE_MINIMUM(1),   0xE0.toByte(),
            USAGE_MAXIMUM(1),   0xE7.toByte(),
            LOGICAL_MINIMUM(1), 0x00,
            LOGICAL_MAXIMUM(1), 0x01,
            REPORT_SIZE(1),     0x01,       // 1 bit each
            REPORT_COUNT(1),    0x08,       // 8 bits for modifier keys
            INPUT(1),           0x02,       // Data, Var, Abs (Modifier byte)
            REPORT_COUNT(1),    0x01,       // 1 report
            REPORT_SIZE(1),     0x08,       // 8 bits for reserved byte
            INPUT(1),           0x01,       // Cnst, Ary, Abs (Reserved byte)
            REPORT_COUNT(1),    0x05,       // 5 reports
            REPORT_SIZE(1),     0x01,       // 1 bit each
            USAGE_PAGE(1),      0x08,       // LEDs
            USAGE_MINIMUM(1),   0x01,       // Num Lock
            USAGE_MAXIMUM(1),   0x05,       // Kana
            OUTPUT(1),          0x02,       // Data, Var, Abs (LED report)
            REPORT_COUNT(1),    0x01,       // 1 report
            REPORT_SIZE(1),     0x03,       // 3 bits for padding
            OUTPUT(1),          0x01,       // Cnst, Ary, Abs (LED report padding)
            REPORT_COUNT(1),    0x06,       // 6 reports
            REPORT_SIZE(1),     0x08,       // 8 bits each
            LOGICAL_MINIMUM(1), 0x00,
            LOGICAL_MAXIMUM(1), 0x65,       // Max key code (usually 101 for 0x65)
            USAGE_PAGE(1),      0x07,       // Key Codes
            USAGE_MINIMUM(1),   0x00,
            USAGE_MAXIMUM(1),   0x65,
            INPUT(1),           0x00,       // Data, Ary, Abs (Key array)
            END_COLLECTION(0)
        )

        // Helper for US Keyboard layout (Modifier)
        fun modifier(char: Char): Byte {
            return when (char) {
                in 'A'..'Z', '!', '@', '#', '$', '%', '^', '&', '*', '(', ')', '_', '+', '{', '}', '|', ':', '"', '~', '<', '>', '?' -> MODIFIER_KEY_SHIFT
                else -> MODIFIER_KEY_NONE
            }
        }

        // Helper for US Keyboard layout (KeyCode)
        fun keyCode(char: Char): Byte {
            return when (char) {
                'a', 'A' -> KEY_A; 'b', 'B' -> KEY_B; 'c', 'C' -> 0x06.toByte()
                'd', 'D' -> 0x07.toByte(); 'e', 'E' -> 0x08.toByte(); 'f', 'F' -> 0x09.toByte()
                'g', 'G' -> 0x0a.toByte(); 'h', 'H' -> 0x0b.toByte(); 'i', 'I' -> 0x0c.toByte()
                'j', 'J' -> 0x0d.toByte(); 'k', 'K' -> 0x0e.toByte(); 'l', 'L' -> 0x0f.toByte()
                'm', 'M' -> 0x10.toByte(); 'n', 'N' -> 0x11.toByte(); 'o', 'O' -> 0x12.toByte()
                'p', 'P' -> 0x13.toByte(); 'q', 'Q' -> 0x14.toByte(); 'r', 'R' -> 0x15.toByte()
                's', 'S' -> 0x16.toByte(); 't', 'T' -> 0x17.toByte(); 'u', 'U' -> 0x18.toByte()
                'v', 'V' -> 0x19.toByte(); 'w', 'W' -> 0x1a.toByte(); 'x', 'X' -> 0x1b.toByte()
                'y', 'Y' -> 0x1c.toByte(); 'z', 'Z' -> KEY_Z
                '1', '!' -> KEY_1; '2', '@' -> 0x1f.toByte(); '3', '#' -> 0x20.toByte()
                '4', '$' -> 0x21.toByte(); '5', '%' -> 0x22.toByte(); '6', '^' -> 0x23.toByte()
                '7', '&' -> 0x24.toByte(); '8', '*' -> 0x25.toByte(); '9', '(' -> 0x26.toByte()
                '0', ')' -> KEY_0
                '\n' -> KEY_ENTER // LF (Enter)
                '\b' -> KEY_BACKSPACE // BS (Backspace)
                '\t' -> KEY_TAB // TAB
                ' ' -> KEY_SPACE
                '-', '_' -> 0x2d.toByte()
                '=', '+' -> 0x2e.toByte()
                '[', '{' -> 0x2f.toByte()
                ']', '}' -> 0x30.toByte()
                '\\', '|' -> 0x31.toByte()
                // ';', ':' -> 0x33.toByte() // Non-US keyboards might differ, original code has this
                // ''', '"' -> 0x34.toByte() // Original code
                // '`', '~' -> 0x35.toByte() // Original code
                // ',', '<' -> 0x36.toByte() // Original code
                // '.', '>' -> 0x37.toByte() // Original code
                // '/', '?' -> 0x38.toByte() // Original code
                // For simplicity, mapping common ones. Add more as needed based on original.
                 ';', ':' -> 0x33.toByte();
                '\'', '"' -> 0x34.toByte();
                '`', '~' -> 0x35.toByte();
                ',', '<' -> 0x36.toByte();
                '.', '>' -> 0x37.toByte();
                '/', '?' -> 0x38.toByte();
                else -> 0x00.toByte() // Ensure return type is Byte
            }
        }
         private const val KEY_PACKET_MODIFIER_KEY_INDEX = 0 // Report ID (if used), then Modifier
         private const val KEY_PACKET_RESERVED_INDEX = 1
         private const val KEY_PACKET_KEY_START_INDEX = 2 // Start of key codes
         private val EMPTY_KEY_REPORT = byteArrayOf(0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00) // ReportID, Modifier, Reserved, 6 keys
    }


    override fun getReportMap(): ByteArray {
        return REPORT_MAP
    }

    /**
     * Sends a sequence of key presses.
     * @param text The string to send.
     */
    fun sendKeys(text: String) {
        for (char in text) {
            val mod = modifier(char)
            val key = keyCode(char)

            if (key != 0x00.toByte()) { // If recognized key
                sendKeyDown(mod, key)
                sendKeyUp() // Release all keys
            }
        }
    }

    /**
     * Sends a key down event.
     * @param modifier Modifier keys (e.g., MODIFIER_KEY_SHIFT)
     * @param keyCode The key code to send.
     */
    fun sendKeyDown(modifier: Byte, keyCode: Byte) {
        // Report format: [Report ID], [Modifier], [Reserved], [Key1], [Key2], ..., [Key6]
        // For this report map, Report ID is 1.
        val report = ByteArray(9) // Report ID (1) + Modifier (1) + Reserved (1) + 6 keys (6)
        report[0] = 0x01 // Report ID
        report[KEY_PACKET_MODIFIER_KEY_INDEX + 1] = modifier // Modifier is after Report ID
        report[KEY_PACKET_RESERVED_INDEX + 1] = 0x00 // Reserved
        report[KEY_PACKET_KEY_START_INDEX + 1] = keyCode // First key
        // Fill rest with 0x00 (no other keys pressed)
        for (i in (KEY_PACKET_KEY_START_INDEX + 1 + 1) until report.size) {
            report[i] = 0x00
        }
        Log.d(TAG, "sendKeyDown: Modifier: $modifier, KeyCode: $keyCode, Report: ${report.contentToString()}")
        addInputReport(report)
    }

    /**
     * Sends a key up event (releases all keys).
     */
    fun sendKeyUp() {
         // Report ID 1, No modifier, No keys pressed
        val report = EMPTY_KEY_REPORT.clone() // Use clone to be safe
        Log.d(TAG, "sendKeyUp: Report: ${report.contentToString()}")
        addInputReport(report)
    }

    override fun onOutputReport(outputReport: ByteArray) {
        // This is where you would handle LED status from the host
        // outputReport typically contains a byte indicating LED states (Num Lock, Caps Lock, Scroll Lock)
        if (outputReport.isEmpty()) {
            Log.w(TAG, "Output Report is empty.")
            return
        }

        val ledStatus: Byte
        // The keyboard's report map has REPORT_ID 0x01 for its collection,
        // so the host should send the Report ID.
        if (outputReport[0] == 0x01.toByte() && outputReport.size >= 2) {
            // Report ID is present, data is the second byte
            ledStatus = outputReport[1]
            Log.d(TAG, "Output Report (LED status) received with Report ID 1: Value = ${String.format("%02X", ledStatus)}")
        } else if (outputReport.size == 1) {
            // Fallback if only one byte is received (less common for reports with explicit IDs)
            ledStatus = outputReport[0]
            Log.d(TAG, "Output Report (LED status) received (assumed raw 1-byte data): Value = ${String.format("%02X", ledStatus)}")
        } else {
            Log.w(TAG, "Unexpected Output Report format or length: ${outputReport.contentToString()}")
            return
        }

        // Example: outputReport[0] might have bits for NumLock (bit 0), CapsLock (bit 1), etc.
        // val numLock = (ledStatus.toInt() and 0x01) != 0
        // val capsLock = (ledStatus.toInt() and 0x02) != 0
        // val scrollLock = (ledStatus.toInt() and 0x04) != 0
        // Log.d(TAG, "NumLock: $numLock, CapsLock: $capsLock, ScrollLock: $scrollLock")
        // Update UI or internal state if needed
    }
} 