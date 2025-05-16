// This is a basic Flutter integration test.
//
// Since integration tests run in a full Flutter application, they can interact
// with the host side of a plugin implementation, unlike Dart unit tests.
//
// For more information about Flutter integration tests, please see
// https://flutter.dev/to/integration-testing


import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

import 'package:flutter_ble_hid_peripheral/flutter_ble_hid_peripheral.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('isBlePeripheralSupported test', (WidgetTester tester) async {
    // The integration test interacts with the real platform implementation,
    // so the result depends on the device it runs on.
    // We primarily test that the call doesn't crash and returns a boolean.
    final bool isSupported = await FlutterBleHidPeripheral.isBlePeripheralSupported();
    expect(isSupported, isA<bool>());
    print('BLE Peripheral Supported (Integration Test): $isSupported');
  });
}
