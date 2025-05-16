import 'dart:async';
import 'flutter_ble_hid_peripheral_platform_interface.dart';

export 'flutter_ble_hid_peripheral_platform_interface.dart' show BleConnectionState;

class FlutterBleHidPeripheral {
  /// Provides a stream of BLE connection state changes.
  ///
  /// Emits [BleConnectionState] objects when a device connects or disconnects,
  /// or when the connection status changes.
  static Stream<BleConnectionState> get connectionStateChanged {
    return FlutterBleHidPeripheralPlatform.instance.connectionStateChanged;
  }

  /// Checks if all necessary Bluetooth permissions have been granted.
  ///
  /// On Android, this checks for `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`,
  /// and `BLUETOOTH_ADVERTISE` on Android 12+, or `BLUETOOTH`,
  /// `BLUETOOTH_ADMIN`, and `ACCESS_FINE_LOCATION` on older versions.
  static Future<bool> hasPermissions() {
    return FlutterBleHidPeripheralPlatform.instance.hasPermissions();
  }

  /// Requests necessary Bluetooth permissions from the user.
  ///
  /// This will trigger a system dialog asking the user to grant permissions.
  /// It's recommended to call [hasPermissions] afterwards to confirm.
  static Future<void> requestPermissions() {
    return FlutterBleHidPeripheralPlatform.instance.requestPermissions();
  }

  /// Starts advertising as a BLE Keyboard.
  ///
  /// [deviceName] is the name that will be advertised by the BLE peripheral.
  /// If null, a default name (e.g., "Flutter KB") will be used.
  static Future<void> startKeyboard({String? deviceName}) {
    return FlutterBleHidPeripheralPlatform.instance.startKeyboard(deviceName: deviceName);
  }

  /// Stops advertising as a BLE Keyboard.
  static Future<void> stopKeyboard() {
    return FlutterBleHidPeripheralPlatform.instance.stopKeyboard();
  }

  /// Sends a sequence of key presses as a BLE Keyboard.
  ///
  /// [keys] is the string to send. Each character will be translated
  /// to its corresponding keycode and modifier (e.g., 'A' becomes SHIFT + 'a').
  static Future<void> sendKeys(String keys) {
    return FlutterBleHidPeripheralPlatform.instance.sendKeys(keys);
  }

  /// Checks if the device supports BLE peripheral mode.
  ///
  /// Requires Android Lollipop (API 21) or higher.
  static Future<bool> isBlePeripheralSupported() {
    return FlutterBleHidPeripheralPlatform.instance.isBlePeripheralSupported();
  }

  /// Checks if Bluetooth is currently enabled on the device.
  static Future<bool> isBluetoothEnabled() {
    return FlutterBleHidPeripheralPlatform.instance.isBluetoothEnabled();
  }

  // --- Mouse Methods ---

  /// Starts advertising as a BLE Mouse.
  ///
  /// [deviceName] is the name that will be advertised by the BLE peripheral.
  /// If null, a default name (e.g., "Flutter Mouse") will be used.
  static Future<void> startMouse({String? deviceName}) {
    return FlutterBleHidPeripheralPlatform.instance.startMouse(deviceName: deviceName);
  }

  /// Stops advertising as a BLE Mouse.
  static Future<void> stopMouse() {
    return FlutterBleHidPeripheralPlatform.instance.stopMouse();
  }

  /// Sends mouse movement and button events.
  ///
  /// [dx]: Change in X-axis.
  /// [dy]: Change in Y-axis.
  /// [dWheel]: Change in scroll wheel.
  /// [buttons]: Integer representing button states. 
  ///   - Bit 0 (0x01): Left button
  ///   - Bit 1 (0x02): Right button
  ///   - Bit 2 (0x04): Middle button
  static Future<void> sendMouseMovement({
    required int dx,
    required int dy,
    required int dWheel,
    required int buttons,
  }) {
    return FlutterBleHidPeripheralPlatform.instance.sendMouseMovement(
      dx: dx,
      dy: dy,
      dWheel: dWheel,
      buttons: buttons,
    );
  }

  /// Disconnects all currently connected BLE devices from the peripheral.
  static Future<void> disconnectAllDevices() {
    return FlutterBleHidPeripheralPlatform.instance.disconnectAllDevices();
  }
}
