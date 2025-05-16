# flutter_ble_hid_peripheral

A Flutter plugin that allows your Flutter application to act as a Bluetooth Low Energy (BLE) Human Interface Device (HID) peripheral, specifically for emulating a keyboard and a mouse.

**Important Note: This project is currently not working. If you have any ideas or clues on how to fix it, please feel free to contact me.**

## Features

*   Advertise as a BLE HID Keyboard
*   Send keystrokes (letters, numbers, symbols, modifiers)
*   Advertise as a BLE HID Mouse
*   Send mouse movements (X, Y, wheel)
*   Send mouse button clicks (left, right, middle)
*   Connection state monitoring
*   Android support (iOS support is not yet implemented)

## Installation

Add this to your package's `pubspec.yaml` file:

```yaml
dependencies:
  flutter_ble_hid_peripheral: ^latest_version # Replace with the actual latest version
```

Then run `flutter pub get`.

## Permissions

### Android

Add the following permissions to your `android/app/src/main/AndroidManifest.xml` file if they are not already present:

```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<!-- For Android 11 (API 30) and below, if you target older versions -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="30" />
```

You will also need to handle runtime permissions for Bluetooth using a package like `permission_handler`.

## Basic Usage

```dart
import 'package:flutter_ble_hid_peripheral/flutter_ble_hid_peripheral.dart';
import 'package:permission_handler/permission_handler.dart'; // For permission requests

// --- Initialization and Permissions ---
Future<void> _initAndCheckPermissions() async {
  // Check and request Bluetooth permissions
  var advertiseStatus = await Permission.bluetoothAdvertise.request();
  var connectStatus = await Permission.bluetoothConnect.request();
  // ... check other necessary permissions like bluetoothScan if needed for your app logic

  if (advertiseStatus.isGranted && connectStatus.isGranted) {
    // Permissions granted
    // You might want to initialize the plugin or check Bluetooth state here
    bool isBluetoothEnabled = await FlutterBleHidPeripheral.isBluetoothEnabled();
    if (isBluetoothEnabled) {
      // Bluetooth is on
    } else {
      // Prompt user to turn on Bluetooth
    }
  } else {
    // Permissions not granted
  }
}


// --- Keyboard Example ---
Future<void> startKeyboard() async {
  try {
    await FlutterBleHidPeripheral.startKeyboard(deviceName: "My Flutter Keyboard");
    print("Keyboard started");
  } catch (e) {
    print("Error starting keyboard: $e");
  }
}

Future<void> sendKeys() async {
  try {
    // Example: Send "Hello"
    await FlutterBleHidPeripheral.sendKeys([KeyboardKey.keyH, KeyboardKey.keyE, KeyboardKey.keyL, KeyboardKey.keyL, KeyboardKey.keyO]);
    // Example: Send Shift + A
    await FlutterBleHidPeripheral.sendKeys([KeyboardKey.modifierLeftShift, KeyboardKey.keyA]);
    print("Keys sent");
  } catch (e) {
    print("Error sending keys: $e");
  }
}

Future<void> stopKeyboard() async {
  try {
    await FlutterBleHidPeripheral.stopKeyboard();
    print("Keyboard stopped");
  } catch (e) {
    print("Error stopping keyboard: $e");
  }
}


// --- Mouse Example ---
Future<void> startMouse() async {
  try {
    await FlutterBleHidPeripheral.startMouse(deviceName: "My Flutter Mouse");
    print("Mouse started");
  } catch (e) {
    print("Error starting mouse: $e");
  }
}

Future<void> moveMouse() async {
  try {
    // Move mouse right by 10 units and down by 5 units
    await FlutterBleHidPeripheral.sendMouseMovement(dx: 10, dy: 5, dWheel: 0, buttons: 0);
    print("Mouse moved");
  } catch (e) {
    print("Error moving mouse: $e");
  }
}

Future<void> clickMouse() async {
  try {
    // Simulate left button press
    await FlutterBleHidPeripheral.sendMouseMovement(dx: 0, dy: 0, dWheel: 0, buttons: MouseButton.left);
    // Simulate left button release (important!)
    await FlutterBleHidPeripheral.sendMouseMovement(dx: 0, dy: 0, dWheel: 0, buttons: 0);
    print("Mouse clicked");
  } catch (e) {
    print("Error clicking mouse: $e");
  }
}

Future<void> stopMouse() async {
  try {
    await FlutterBleHidPeripheral.stopMouse();
    print("Mouse stopped");
  } catch (e) {
    print("Error stopping mouse: $e");
  }
}

// Remember to listen to connection state changes:
// FlutterBleHidPeripheral.connectionState.listen((BleConnectionState state) {
//   print("Connection state: $state");
// });

```

## TODO

*   iOS Implementation
*   Support for more complex HID reports (e.g., consumer control for media keys)
*   More comprehensive example application

Feel free to contribute!

