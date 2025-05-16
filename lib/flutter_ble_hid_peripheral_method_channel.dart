import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'flutter_ble_hid_peripheral_platform_interface.dart';

/// An implementation of [FlutterBleHidPeripheralPlatform] that uses method channels.
class MethodChannelFlutterBleHidPeripheral extends FlutterBleHidPeripheralPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final MethodChannel methodChannel = const MethodChannel('flutter_ble_hid_peripheral');

  // StreamController for connection state changes
  final StreamController<BleConnectionState> _connectionStateController = StreamController.broadcast();

  MethodChannelFlutterBleHidPeripheral() {
    methodChannel.setMethodCallHandler(_handleMethodCall);
  }

  Future<dynamic> _handleMethodCall(MethodCall call) async {
    switch (call.method) {
      case 'onConnectionStateChanged':
        final Map<dynamic, dynamic>? args = call.arguments as Map<dynamic, dynamic>?;
        if (args != null) {
          _connectionStateController.add(
            BleConnectionState(
              deviceId: args['deviceId'] as String? ?? 'Unknown Device',
              deviceName: args['deviceName'] as String?,
              status: args['status'] as int? ?? -1,
              newState: args['newState'] as int? ?? -1,
            ),
          );
        }
        break;
      case 'onLog':
        final String? message = call.arguments as String?;
        if (message != null) {
          debugPrint('[Native Log] $message');
        }
        break;
      default:
        // Handle other method calls if any (e.g., for events not fitting a direct method response)
        debugPrint('Unhandled method call from native: ${call.method}');
    }
  }

  @override
  Stream<BleConnectionState> get connectionStateChanged => _connectionStateController.stream;

  @override
  Future<bool> hasPermissions() async {
    final bool? result = await methodChannel.invokeMethod<bool>('hasPermissions');
    return result ?? false;
  }

  @override
  Future<void> requestPermissions() async {
    await methodChannel.invokeMethod<void>('requestPermissions');
  }

  @override
  Future<void> startKeyboard({String? deviceName}) async {
    await methodChannel.invokeMethod<void>('startKeyboard', {'name': deviceName});
  }

  @override
  Future<void> stopKeyboard() async {
    await methodChannel.invokeMethod<void>('stopKeyboard');
  }

  @override
  Future<void> sendKeys(String keys) async {
    await methodChannel.invokeMethod<void>('sendKeys', {'keys': keys});
  }

  @override
  Future<bool> isBlePeripheralSupported() async {
    final bool? result = await methodChannel.invokeMethod<bool>('isBlePeripheralSupported');
    return result ?? false;
  }

  @override
  Future<bool> isBluetoothEnabled() async {
    final bool? result = await methodChannel.invokeMethod<bool>('isBluetoothEnabled');
    return result ?? false;
  }

  // --- Mouse Methods Implementation ---
  @override
  Future<void> startMouse({String? deviceName}) async {
    await methodChannel.invokeMethod<void>('startMouse', {'name': deviceName});
  }

  @override
  Future<void> stopMouse() async {
    await methodChannel.invokeMethod<void>('stopMouse');
  }

  @override
  Future<void> sendMouseMovement({
    required int dx,
    required int dy,
    required int dWheel,
    required int buttons,
  }) async {
    await methodChannel.invokeMethod<void>('sendMouseMovement', {
      'dx': dx,
      'dy': dy,
      'dWheel': dWheel,
      'buttons': buttons,
    });
  }

  @override
  Future<void> disconnectAllDevices() async {
    await methodChannel.invokeMethod<void>('disconnectAllDevices');
  }

  // Dispose the stream controller when no longer needed (though platform channels are typically long-lived)
  // For a plugin like this, it might not be strictly necessary to manually close it unless the plugin itself is disposed.
  // void dispose() {
  //   _connectionStateController.close();
  // }
}
