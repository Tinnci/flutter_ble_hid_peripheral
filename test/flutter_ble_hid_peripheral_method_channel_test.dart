import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_ble_hid_peripheral/flutter_ble_hid_peripheral_method_channel.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  MethodChannelFlutterBleHidPeripheral platform = MethodChannelFlutterBleHidPeripheral();
  const MethodChannel channel = MethodChannel('flutter_ble_hid_peripheral');

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
      channel,
      (MethodCall methodCall) async {
        if (methodCall.method == 'isBlePeripheralSupported') {
          return true; // Mock a successful response
        }
        return null; // Default response for other methods
      },
    );
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, null);
  });

  test('isBlePeripheralSupported returns true when supported', () async {
    expect(await platform.isBlePeripheralSupported(), isTrue);
  });
}
