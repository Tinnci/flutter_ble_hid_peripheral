import 'dart:async';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'flutter_ble_hid_peripheral_method_channel.dart';

/// Represents the state of a BLE connection.
class BleConnectionState {
  final String deviceId;
  final String? deviceName; // Nullable as it might not always be available
  final int status;
  final int newState;

  BleConnectionState({
    required this.deviceId,
    this.deviceName,
    required this.status,
    required this.newState,
  });

  @override
  String toString() {
    return 'BleConnectionState(deviceId: $deviceId, deviceName: $deviceName, status: $status, newState: $newState)';
  }
}

abstract class FlutterBleHidPeripheralPlatform extends PlatformInterface {
  /// Constructs a FlutterBleHidPeripheralPlatform.
  FlutterBleHidPeripheralPlatform() : super(token: _token);

  static final Object _token = Object();

  static FlutterBleHidPeripheralPlatform _instance = MethodChannelFlutterBleHidPeripheral();

  /// The default instance of [FlutterBleHidPeripheralPlatform] to use.
  ///
  /// Defaults to [MethodChannelFlutterBleHidPeripheral].
  static FlutterBleHidPeripheralPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [FlutterBleHidPeripheralPlatform] when
  /// they register themselves.
  static set instance(FlutterBleHidPeripheralPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  /// Stream for BLE connection state changes.
  Stream<BleConnectionState> get connectionStateChanged {
    throw UnimplementedError('connectionStateChanged has not been implemented.');
  }

  Future<bool> hasPermissions() {
    throw UnimplementedError('hasPermissions() has not been implemented.');
  }

  Future<void> requestPermissions() {
    throw UnimplementedError('requestPermissions() has not been implemented.');
  }

  Future<void> startKeyboard({String? deviceName}) {
    throw UnimplementedError('startKeyboard() has not been implemented.');
  }

  Future<void> stopKeyboard() {
    throw UnimplementedError('stopKeyboard() has not been implemented.');
  }

  Future<void> sendKeys(String keys) {
    throw UnimplementedError('sendKeys() has not been implemented.');
  }

  Future<bool> isBlePeripheralSupported() {
    throw UnimplementedError('isBlePeripheralSupported() has not been implemented.');
  }

  Future<bool> isBluetoothEnabled() {
    throw UnimplementedError('isBluetoothEnabled() has not been implemented.');
  }

  // --- Mouse Methods ---
  Future<void> startMouse({String? deviceName}) {
    throw UnimplementedError('startMouse() has not been implemented.');
  }

  Future<void> stopMouse() {
    throw UnimplementedError('stopMouse() has not been implemented.');
  }

  Future<void> sendMouseMovement({
    required int dx,
    required int dy,
    required int dWheel,
    required int buttons, // Bit 0: Left, Bit 1: Right, Bit 2: Middle
  }) {
    throw UnimplementedError('sendMouseMovement() has not been implemented.');
  }

  Future<void> disconnectAllDevices() {
    throw UnimplementedError('disconnectAllDevices() has not been implemented.');
  }
}
