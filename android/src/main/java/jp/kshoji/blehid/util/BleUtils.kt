package jp.kshoji.blehid.util

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.NonNull

/**
 * Utilities for Bluetooth LE
 *
 * @author K.Shoji
 */
object BleUtils { // Changed to object

    /**
     * Check if Bluetooth LE device supported on the running environment.
     *
     * @param context the context
     * @return true if supported
     */
    fun isBleSupported(@NonNull context: Context): Boolean {
        return try {
            if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                return false
            }

            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            // Consolidated adapter retrieval
            val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
            bluetoothAdapter != null
        } catch (ignored: Throwable) {
            // ignore exception
            false
        }
    }

    /**
     * Check if Bluetooth LE Peripheral mode supported on the running environment.
     *
     * @param context the context
     * @return true if supported
     */
    @SuppressLint("NewApi")
    fun isBlePeripheralSupported(@NonNull context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter ?: return false

        return bluetoothAdapter.isMultipleAdvertisementSupported
    }

    /**
     * Check if bluetooth function enabled
     *
     * @param context the context
     * @return true if bluetooth enabled
     */
    fun isBluetoothEnabled(@NonNull context: Context): Boolean {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        // Consolidated adapter retrieval
        val bluetoothAdapter = bluetoothManager?.adapter ?: return false
        return bluetoothAdapter.isEnabled
    }

    /**
     * Request code for bluetooth enabling
     */
    const val REQUEST_CODE_BLUETOOTH_ENABLE = 0xb1e

    /**
     * Enables bluetooth function.<br />
     * the Activity may implement the `onActivityResult` method with the request code `REQUEST_CODE_BLUETOOTH_ENABLE`.
     *
     * @param activity the activity
     */
    fun enableBluetooth(@NonNull activity: Activity) {
        activity.startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_CODE_BLUETOOTH_ENABLE)
    }
} 