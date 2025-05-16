package com.example.flutter_ble_hid_peripheral

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.NonNull
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import jp.kshoji.blehid.KeyboardPeripheral // 导入您的 Kotlin peripheral
import jp.kshoji.blehid.MousePeripheral    // <--- 添加 MousePeripheral 导入
// import jp.kshoji.blehid.HidPeripheral // 如果直接用 ConnectionState

/** FlutterBleHidPeripheralPlugin */
class FlutterBleHidPeripheralPlugin: FlutterPlugin, MethodCallHandler, ActivityAware {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel : MethodChannel
  private var applicationContext: Context? = null
  private var keyboardPeripheral: KeyboardPeripheral? = null
  private var mousePeripheral: MousePeripheral? = null // <--- 添加 mousePeripheral 实例
  private var activity: Activity? = null

  companion object {
    private const val TAG = "FlutterBleHidPlugin"
    private const val PERMISSION_REQUEST_CODE = 101 // 可以是任何唯一的整数
  }

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flutter_ble_hid_peripheral")
    channel.setMethodCallHandler(this)
    applicationContext = flutterPluginBinding.applicationContext
    Log.d(TAG, "Plugin onAttachedToEngine")
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    val appContext = applicationContext
    if (appContext == null) {
      result.error("NO_CONTEXT", "Application context is null. Plugin not properly initialized.", null)
      return
    }

    // 权限检查应该优先于大多数方法调用
    if (call.method != "isBlePeripheralSupported" && call.method != "isBluetoothEnabled" && call.method != "requestPermissions" && call.method != "hasPermissions") {
      if (!hasRequiredPermissions(appContext)) {
        result.error("PERMISSION_DENIED", "Required Bluetooth permissions not granted. Call requestPermissions first.", null)
        return
      }
    }

    try {
      when (call.method) {
        "requestPermissions" -> {
          if (activity != null) {
            requestBluetoothPermissions(activity!!)
            result.success(true) //  Dart side should await and then re-check status
          } else {
            result.error("NO_ACTIVITY", "Activity is null, cannot request permissions.", null)
          }
        }
        "hasPermissions" -> {
          result.success(hasRequiredPermissions(appContext))
        }
        "startKeyboard" -> {
          if (mousePeripheral != null) { // <--- 如果鼠标已启动，先停止
            mousePeripheral?.stopAdvertising()
            mousePeripheral = null
          }
          if (keyboardPeripheral == null) {
            keyboardPeripheral = KeyboardPeripheral(appContext)
            keyboardPeripheral?.deviceName = call.argument<String>("name") ?: "Flutter KB"
            // Setup connection state callback to inform Dart
            keyboardPeripheral?.connectionStateCallback = { connState ->
              val map = mapOf(
                "deviceId" to connState.device.address, // Renamed for clarity
                "deviceName" to connState.device.name,
                "status" to connState.status,
                "newState" to connState.newState
              )
              // Ensure this is called on the UI thread for channel communication
              activity?.runOnUiThread {
                channel.invokeMethod("onConnectionStateChanged", map)
              }
            }
          }
          keyboardPeripheral?.startAdvertising()
          result.success(null)
        }
        "stopKeyboard" -> {
          keyboardPeripheral?.stopAdvertising()
          // keyboardPeripheral = null // Consider if instance should be released or reused
          result.success(null)
        }
        "sendKeys" -> {
          val text: String? = call.argument("keys")
          if (text != null) {
            keyboardPeripheral?.sendKeys(text)
            result.success(null)
          } else {
            result.error("INVALID_ARG", "Missing 'keys' argument for sendKeys.", null)
          }
        }
        "startMouse" -> { // <--- 添加 startMouse
          if (keyboardPeripheral != null) { // <--- 如果键盘已启动，先停止
            keyboardPeripheral?.stopAdvertising()
            keyboardPeripheral = null
          }
          if (mousePeripheral == null) {
            mousePeripheral = MousePeripheral(appContext)
            mousePeripheral?.deviceName = call.argument<String>("name") ?: "Flutter Mouse"
            mousePeripheral?.connectionStateCallback = { connState ->
              val map = mapOf(
                "deviceId" to connState.device.address,
                "deviceName" to connState.device.name,
                "status" to connState.status,
                "newState" to connState.newState
              )
              activity?.runOnUiThread {
                channel.invokeMethod("onConnectionStateChanged", map)
              }
            }
          }
          mousePeripheral?.startAdvertising()
          result.success(null)
        }
        "stopMouse" -> { // <--- 添加 stopMouse
          mousePeripheral?.stopAdvertising()
          result.success(null)
        }
        "sendMouseMovement" -> { // <--- 添加 sendMouseMovement
          val dx = call.argument<Int>("dx")
          val dy = call.argument<Int>("dy")
          val dWheel = call.argument<Int>("dWheel")
          val buttons = call.argument<Int>("buttons") // Flutter会发送Int，转为Byte

          if (dx != null && dy != null && dWheel != null && buttons != null) {
            mousePeripheral?.sendMovement(dx, dy, dWheel, buttons.toByte())
            result.success(null)
          } else {
            result.error("INVALID_ARG", "Missing arguments for sendMouseMovement (dx, dy, dWheel, buttons required).", null)
          }
        }
        "isBlePeripheralSupported" -> {
          result.success(jp.kshoji.blehid.util.BleUtils.isBlePeripheralSupported(appContext))
        }
        "isBluetoothEnabled" -> {
          result.success(jp.kshoji.blehid.util.BleUtils.isBluetoothEnabled(appContext))
        }
        // Placeholder for other device types
        // "startMouse" -> result.notImplemented()
        "disconnectAllDevices" -> {
          if (keyboardPeripheral != null) {
            keyboardPeripheral?.disconnectAllConnectedDevices()
            // Assuming disconnect might imply we are no longer actively trying to use this peripheral instance for new connections immediately
            // but let's not null it out here, stopAdvertising should be used for that.
            _logToDart("disconnectAllDevices called on keyboardPeripheral")
          } else if (mousePeripheral != null) {
            mousePeripheral?.disconnectAllConnectedDevices()
            _logToDart("disconnectAllDevices called on mousePeripheral")
          } else {
            _logToDart("disconnectAllDevices called but no active peripheral.")
            result.success(null) // or specific result indicating no active peripheral
            return
          }
          result.success(null)
        }
        else -> result.notImplemented()
      }
    } catch (e: UnsupportedOperationException) {
      Log.e(TAG, "Operation not supported: ${e.message}", e)
      result.error("BLE_UNSUPPORTED", e.message, e.stackTraceToString())
    } catch (e: SecurityException) {
      Log.e(TAG, "Security Exception: ${e.message}", e)
      result.error("PERMISSION_ERROR", "A Bluetooth permission may be missing or was denied: ${e.message}", e.stackTraceToString())
    } catch (e: Exception) {
      Log.e(TAG, "Native call error (${call.method}): ${e.message}", e)
      result.error("NATIVE_ERROR", "An unexpected error occurred: ${e.message}", e.stackTraceToString())
    }
  }

  private fun getRequiredPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
      arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADVERTISE
      )
    } else { // Android 6 - 11
      arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.ACCESS_FINE_LOCATION
      )
    }
  }

  private fun hasRequiredPermissions(context: Context): Boolean {
    return getRequiredPermissions().all {
      ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
  }

  private fun requestBluetoothPermissions(activity: Activity) {
    val missingPermissions = getRequiredPermissions().filter {
      ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
    }
    if (missingPermissions.isNotEmpty()) {
      ActivityCompat.requestPermissions(activity, missingPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    keyboardPeripheral?.stopAdvertising() // Clean up resources
    mousePeripheral?.stopAdvertising() // <--- 清理鼠标资源
    applicationContext = null
    Log.d(TAG, "Plugin onDetachedFromEngine")
  }

  // ActivityAware methods
  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
    Log.d(TAG, "Plugin onAttachedToActivity")
  }

  override fun onDetachedFromActivityForConfigChanges() {
    activity = null
    Log.d(TAG, "Plugin onDetachedFromActivityForConfigChanges")
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    activity = binding.activity
    Log.d(TAG, "Plugin onReattachedToActivityForConfigChanges")
  }

  override fun onDetachedFromActivity() {
    activity = null
    Log.d(TAG, "Plugin onDetachedFromActivity")
  }

  private fun _logToDart(message: String) {
    activity?.runOnUiThread {
        channel.invokeMethod("onLog", message)
    }
  }
}
