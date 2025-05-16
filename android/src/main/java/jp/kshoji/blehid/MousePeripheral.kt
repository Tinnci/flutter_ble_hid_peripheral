package jp.kshoji.blehid

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * BLE Mouse Peripheral
 * Based on reference from HidConsts.Descriptor for mouse.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class MousePeripheral(context: Context) : HidPeripheral(
    context,
    needInputReport = true,    // Mouse sends input reports
    needOutputReport = false,  // Typically no output reports for a basic mouse
    needFeatureReport = false, // Typically no feature reports for a basic mouse
    dataSendingRate = 10       // Milliseconds, mouse might need faster updates than keyboard
) {

    companion object {
        private val TAG = MousePeripheral::class.java.simpleName
        const val REPORT_ID_MOUSE: Byte = 0x01

        // Button definitions (matching typical HID mouse button order)
        const val BUTTON_1: Byte = 0x01 // Usually Left Button
        const val BUTTON_2: Byte = 0x02 // Usually Right Button
        const val BUTTON_3: Byte = 0x04 // Usually Middle Button
    }

    // Mouse Report Map (Report ID 1)
    // Extracted and adapted from the Java HidConsts.Descriptor
    private val MOUSE_REPORT_MAP = byteArrayOf(
        USAGE_PAGE(1), 0x01.toByte(),       // USAGE_PAGE (Generic Desktop)
        USAGE(1), 0x02.toByte(),       // USAGE (Mouse)
        COLLECTION(1), 0x01.toByte(),       // COLLECTION (Application)
        REPORT_ID(1), REPORT_ID_MOUSE, //   REPORT_ID (1)
        USAGE(1), 0x01.toByte(),       //   USAGE (Pointer)
        COLLECTION(1), 0x00.toByte(),       //   COLLECTION (Physical)
        USAGE_PAGE(1), 0x09.toByte(),       //     USAGE_PAGE (Button)
        USAGE_MINIMUM(1), 0x01.toByte(),       //     USAGE_MINIMUM (Button 1)
        USAGE_MAXIMUM(1), 0x03.toByte(),       //     USAGE_MAXIMUM (Button 3)
        LOGICAL_MINIMUM(1), 0x00.toByte(),       //     LOGICAL_MINIMUM (0)
        LOGICAL_MAXIMUM(1), 0x01.toByte(),       //     LOGICAL_MAXIMUM (1)
        REPORT_COUNT(1), 0x03.toByte(),       //     REPORT_COUNT (3)  ; Buttons (Left, Right, Middle)
        REPORT_SIZE(1), 0x01.toByte(),       //     REPORT_SIZE (1)   ; 1 bit per button
        INPUT(1), 0x02.toByte(),       //     INPUT (Data,Var,Abs)
        REPORT_COUNT(1), 0x01.toByte(),       //     REPORT_COUNT (1)
        REPORT_SIZE(1), 0x05.toByte(),       //     REPORT_SIZE (5)   ; Padding (5 bits to make 1 byte for buttons)
        INPUT(1), 0x03.toByte(),       //     INPUT (Cnst,Var,Abs)
        USAGE_PAGE(1), 0x01.toByte(),       //     USAGE_PAGE (Generic Desktop)
        USAGE(1), 0x30.toByte(),       //     USAGE (X)
        USAGE(1), 0x31.toByte(),       //     USAGE (Y)
        USAGE(1), 0x38.toByte(),       //     USAGE (Wheel)
        LOGICAL_MINIMUM(1), 0x81.toByte(),   //     LOGICAL_MINIMUM (-127)
        LOGICAL_MAXIMUM(1), 0x7F.toByte(),       //     LOGICAL_MAXIMUM (127)
        REPORT_SIZE(1), 0x08.toByte(),       //     REPORT_SIZE (8)   ; 8 bits for X, Y, Wheel
        REPORT_COUNT(1), 0x03.toByte(),       //     REPORT_COUNT (3)  ; X, Y, Wheel
        INPUT(1), 0x06.toByte(),       //     INPUT (Data,Var,Rel)
        END_COLLECTION(0),             //   END_COLLECTION
        END_COLLECTION(0)              // END_COLLECTION
    )

    override fun getReportMap(): ByteArray {
        return MOUSE_REPORT_MAP
    }

    /**
     * Sends mouse movement and button states.
     * @param dx Relative change in X
     * @param dy Relative change in Y
     * @param dWheel Relative change in wheel
     * @param buttons Byte representing button states (Bit 0: Btn1, Bit 1: Btn2, Bit 2: Btn3)
     */
    fun sendMovement(dx: Int, dy: Int, dWheel: Int, buttons: Byte) {
        // Report format: [Report ID], [Buttons], [dX], [dY], [dWheel]
        // Report ID for mouse is 1.
        val report = ByteArray(5)
        report[0] = REPORT_ID_MOUSE       // Report ID
        report[1] = buttons             // Buttons state
        report[2] = dx.toByte()         // dX
        report[3] = dy.toByte()         // dY
        report[4] = dWheel.toByte()     // dWheel

        Log.d(TAG, "sendMovement: Buttons: $buttons, dX: $dx, dY: $dy, dWheel: $dWheel, Report: ${report.contentToString()}")
        addInputReport(report)
    }

    /**
     * Convenience function to send mouse click (button down + up).
     * Note: This sends two reports in quick succession. The actual click effect
     * depends on the host's interpretation and processing speed.
     * For more precise control, send down and up events separately.
     *
     * @param buttons The button(s) to click.
     */
    fun sendClick(buttons: Byte) {
        sendMovement(0,0,0, buttons) // Button down
        // Consider a small delay if needed, but usually handled by dataSendingRate
        sendMovement(0,0,0,0x00) // Button up (all buttons released)
    }


    override fun onOutputReport(outputReport: ByteArray) {
        // Mice typically don't receive output reports unless for very specific features.
        // Log if received, but no action taken.
        Log.d(TAG, "Output Report received for Mouse: ${outputReport.contentToString()}")
    }
} 