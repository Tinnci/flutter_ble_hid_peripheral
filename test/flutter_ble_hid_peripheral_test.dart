import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_ble_hid_peripheral/flutter_ble_hid_peripheral.dart';
import 'package:flutter_ble_hid_peripheral/flutter_ble_hid_peripheral_platform_interface.dart';
import 'package:flutter_ble_hid_peripheral/flutter_ble_hid_peripheral_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockFlutterBleHidPeripheralPlatform
    with MockPlatformInterfaceMixin
    implements FlutterBleHidPeripheralPlatform {

  // Mock implementations for all methods in FlutterBleHidPeripheralPlatform
  @override
  Stream<BleConnectionState> get connectionStateChanged => StreamController<BleConnectionState>().stream; // Return an empty stream or a mock stream

  @override
  Future<bool> hasPermissions() async => true; // Default mock response

  @override
  Future<void> requestPermissions() async {} // Default mock response

  @override
  Future<void> startKeyboard({String? deviceName}) async {} // Default mock response

  @override
  Future<void> stopKeyboard() async {} // Default mock response

  @override
  Future<void> sendKeys(String keys) async {} // Default mock response

  @override
  Future<bool> isBlePeripheralSupported() async => true; // Default mock response

  @override
  Future<bool> isBluetoothEnabled() async => true; // Default mock response
}

void main() {
  final FlutterBleHidPeripheralPlatform initialPlatform = FlutterBleHidPeripheralPlatform.instance;

  test('$MethodChannelFlutterBleHidPeripheral is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelFlutterBleHidPeripheral>());
  });

  test('isBlePeripheralSupported calls platform method', () async {
    MockFlutterBleHidPeripheralPlatform fakePlatform = MockFlutterBleHidPeripheralPlatform();
    FlutterBleHidPeripheralPlatform.instance = fakePlatform;

    // Expect the mock platform's method to be called and return true
    expect(await FlutterBleHidPeripheral.isBlePeripheralSupported(), true);
  });
}
