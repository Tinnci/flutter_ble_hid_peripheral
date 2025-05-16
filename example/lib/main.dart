import 'package:flutter/material.dart';
import 'dart:async';
import 'package:flutter_ble_hid_peripheral/flutter_ble_hid_peripheral.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'BLE HID Peripheral Example',
      theme: ThemeData(
        primarySwatch: Colors.blue,
        visualDensity: VisualDensity.adaptivePlatformDensity,
      ),
      home: const MyHomePage(),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key});

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

enum PeripheralMode { keyboard, mouse }

class _MyHomePageState extends State<MyHomePage> with WidgetsBindingObserver {
  bool _hasPermissions = false;
  bool _isBluetoothEnabled = false;
  bool _isBlePeripheralSupported = false;
  
  // unified advertising state, specific mode started will be tracked by _activePeripheralMode
  bool _isAdvertising = false; 
  PeripheralMode _activePeripheralMode = PeripheralMode.keyboard; // Default to keyboard

  String _deviceName = "FlutterHID"; // Default device name
  final TextEditingController _deviceNameController = TextEditingController();
  final TextEditingController _keysController = TextEditingController();
  StreamSubscription<BleConnectionState>? _connectionStateSubscription;
  BleConnectionState? _connectionState;
  List<String> _logMessages = [];
  int _currentMouseButtons = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _deviceNameController.text = _deviceName;
    _keysController.text = "Hello Flutter!";
    _checkInitialStates();
    _listenToConnectionState();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _connectionStateSubscription?.cancel();
    _deviceNameController.dispose();
    _keysController.dispose();
    // Ensure advertising is stopped if active
    if (_isAdvertising) {
      if (_activePeripheralMode == PeripheralMode.keyboard) {
        FlutterBleHidPeripheral.stopKeyboard().catchError((e) {
          _log("Error stopping keyboard on dispose: $e");
        });
      } else if (_activePeripheralMode == PeripheralMode.mouse) {
        FlutterBleHidPeripheral.stopMouse().catchError((e) {
          _log("Error stopping mouse on dispose: $e");
        });
        // Reset mouse buttons state on stop
        _currentMouseButtons = 0;
      }
    }
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    super.didChangeAppLifecycleState(state);
    // When app comes to foreground, re-check permissions and BT state
    if (state == AppLifecycleState.resumed) {
      _log("App resumed - checking states.");
      _checkInitialStates();
    }
  }

  void _log(String message) {
    debugPrint("[ExampleApp] $message");
    setState(() {
      _logMessages.insert(0, "${TimeOfDay.now().format(context)}: $message");
      if (_logMessages.length > 20) { // Keep log size manageable
          _logMessages.removeLast();
      }
    });
  }

  Future<void> _checkInitialStates() async {
    await _checkPermissions();
    await _checkBluetoothSupportAndState();
  }

  Future<void> _checkBluetoothSupportAndState() async {
    try {
      final supported = await FlutterBleHidPeripheral.isBlePeripheralSupported();
      final enabled = await FlutterBleHidPeripheral.isBluetoothEnabled();
      if (mounted) {
        setState(() {
          _isBlePeripheralSupported = supported;
          _isBluetoothEnabled = enabled;
        });
        _log("BLE Peripheral Supported: $_isBlePeripheralSupported");
        _log("Bluetooth Enabled: $_isBluetoothEnabled");
      }
    } catch (e) {
      _log("Error checking BT/BLE state: $e");
      if (mounted) {
        setState(() {
          _isBlePeripheralSupported = false;
          _isBluetoothEnabled = false;
        });
      }
    }
  }

  Future<void> _checkPermissions() async {
    try {
      final has = await FlutterBleHidPeripheral.hasPermissions();
      if (mounted) {
        setState(() {
          _hasPermissions = has;
        });
        _log("Initial Permissions: $_hasPermissions");
      }
    } catch (e) {
      _log("Error checking permissions: $e");
       if (mounted) {
        setState(() {
          _hasPermissions = false;
        });
      }
    }
  }

  Future<void> _requestPermissions() async {
    try {
      await FlutterBleHidPeripheral.requestPermissions();
      _log("Permission request initiated. Check system dialog.");
      // Android shows a dialog. User interaction is required.
      // We'll re-check after a short delay, or on app resume.
      await Future.delayed(const Duration(milliseconds: 500)); // Give time for dialog
      await _checkPermissions(); // Re-check after request
    } catch (e) {
      _log("Error requesting permissions: $e");
    }
  }

  void _listenToConnectionState() {
    _connectionStateSubscription = FlutterBleHidPeripheral.connectionStateChanged.listen(
      (BleConnectionState state) {
        if (mounted) {
          setState(() {
            _connectionState = state;
          });
          _log("Connection state update: ${state.toString()}");
           if (state.newState == 0) { // STATE_DISCONNECTED = 0 (from BluetoothProfile.java)
             _log("Device disconnected: ${state.deviceId}. Advertising might restart if it was stopped.");
             // if (_isAdvertising) { // If we intended to keep advertising
             //    // Potentially restart advertising or update UI
             // }
          } else if (state.newState == 2) { // STATE_CONNECTED = 2
             _log("Device connected: ${state.deviceId}. Advertising likely stopped by native code.");
          }
        }
      },
      onError: (error) {
        _log("Connection state stream error: $error");
        if (mounted) {
          setState(() {
            _connectionState = null;
          });
        }
      },
    );
  }

  Future<void> _toggleAdvertising() async {
    if (!_hasPermissions) {
      _log("Permissions not granted. Please request permissions first.");
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Permissions not granted!')),
      );
      return;
    }
    if (!_isBluetoothEnabled) {
       _log("Bluetooth is not enabled.");
       ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Bluetooth not enabled!')),
      );
      return;
    }
     if (!_isBlePeripheralSupported) {
       _log("BLE Peripheral mode not supported on this device.");
       ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('BLE Peripheral mode not supported!')),
      );
      return;
    }

    setState(() {
      _deviceName = _deviceNameController.text.trim();
      if (_deviceName.isEmpty) _deviceName = "FlutterHID"; // Fallback
    });

    if (_isAdvertising) {
      try {
        if (_activePeripheralMode == PeripheralMode.keyboard) {
          await FlutterBleHidPeripheral.stopKeyboard();
          _log("Stopped advertising keyboard.");
        } else if (_activePeripheralMode == PeripheralMode.mouse) {
          await FlutterBleHidPeripheral.stopMouse();
          _log("Stopped advertising mouse.");
        }
        if (mounted) {
          setState(() {
            _isAdvertising = false;
          });
        }
      } catch (e) {
        _log("Error stopping peripheral: $e");
      }
    } else {
      try {
        if (_activePeripheralMode == PeripheralMode.keyboard) {
          await FlutterBleHidPeripheral.startKeyboard(deviceName: _deviceName);
          _log("Started advertising keyboard as '$_deviceName'.");
        } else if (_activePeripheralMode == PeripheralMode.mouse) {
          await FlutterBleHidPeripheral.startMouse(deviceName: _deviceName);
          _log("Started advertising mouse as '$_deviceName'.");
        }
        if (mounted) {
          setState(() {
            _isAdvertising = true;
          });
        }
      } catch (e) {
        _log("Error starting peripheral: $e");
      }
    }
  }

  Future<void> _sendKeys() async {
    if (!_isAdvertising || _activePeripheralMode != PeripheralMode.keyboard) {
      _log("Keyboard not advertising. Start keyboard first.");
       ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Keyboard not advertising or not in keyboard mode!')),
      );
      return;
    }
    final keys = _keysController.text;
    if (keys.isEmpty) {
      _log("No keys to send.");
      return;
    }
    try {
      await FlutterBleHidPeripheral.sendKeys(keys);
      _log("Sent keys: '$keys'");
    } catch (e) {
      _log("Error sending keys: $e");
    }
  }

  Future<void> _sendMouseMovement({
    required int dx,
    required int dy,
    required int dWheel,
    required int buttons
  }) async {
    if (!_isAdvertising || _activePeripheralMode != PeripheralMode.mouse) {
      _log("Mouse not advertising. Start mouse first.");
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Mouse not advertising or not in mouse mode!')),
      );
      return;
    }
    try {
      await FlutterBleHidPeripheral.sendMouseMovement(
        dx: dx,
        dy: dy,
        dWheel: dWheel,
        buttons: buttons,
      );
      _log("Sent mouse movement: dx:$dx, dy:$dy, wheel:$dWheel, buttons:$buttons");
    } catch (e) {
      _log("Error sending mouse movement: $e");
    }
  }

  Future<void> _toggleMouseButton(int button) async {
    if (!_isAdvertising || _activePeripheralMode != PeripheralMode.mouse) {
      _log("Mouse not advertising or not in mouse mode.");
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Mouse not advertising or not in mouse mode!')),
      );
      return;
    }
    
    int newButtonState = _currentMouseButtons ^ button;
    if (mounted) {
      setState(() {
        _currentMouseButtons = newButtonState;
      });
    }
    // Send an immediate update of button state with no movement
    await _sendMouseMovement(dx: 0, dy: 0, dWheel: 0, buttons: _currentMouseButtons);
    _log("Mouse button state changed: $_currentMouseButtons");
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('BLE HID Peripheral Example'),
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: ListView(
          children: <Widget>[
            _buildStatusSection(),
            const Divider(),
            _buildControlSection(),
            const Divider(),
            _buildLogSection(),
          ],
        ),
      ),
    );
  }

  Widget _buildStatusSection() {
    return Card(
      elevation: 2,
      child: Padding(
        padding: const EdgeInsets.all(12.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Text("Permissions Granted: ${_hasPermissions ? '✅' : '❌'}", style: TextStyle(color: _hasPermissions ? Colors.green : Colors.red)),
            Text("Bluetooth Enabled: ${_isBluetoothEnabled ? '✅' : '❌'}", style: TextStyle(color: _isBluetoothEnabled ? Colors.green : Colors.red)),
            Text("BLE Peripheral Supported: ${_isBlePeripheralSupported ? '✅' : '❌'}", style: TextStyle(color: _isBlePeripheralSupported ? Colors.green : Colors.red)),
            const SizedBox(height: 8),
            Text("Advertising Status: ${_isAdvertising ? 'Active ($_activePeripheralMode) ✅' : 'Inactive ❌'}", style: TextStyle(color: _isAdvertising ? Colors.green : Colors.orange)),
            const SizedBox(height: 8),
            Text("Connection State:", style: Theme.of(context).textTheme.titleSmall),
            Text(_connectionState?.toString() ?? "No active connection updates."),
          ],
        ),
      ),
    );
  }

   Widget _buildControlSection() {
    return Card(
      elevation: 2,
      child: Padding(
        padding: const EdgeInsets.all(12.0),
        child: Column(
          children: <Widget>[
            ToggleButtons(
              isSelected: [
                _activePeripheralMode == PeripheralMode.keyboard,
                _activePeripheralMode == PeripheralMode.mouse,
              ],
              onPressed: (index) {
                if (_isAdvertising) {
                  _log("Stop advertising before switching mode.");
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Stop advertising before switching mode!')),
                  );
                  return;
                }
                setState(() {
                  _activePeripheralMode = PeripheralMode.values[index];
                  _log("Switched to ${_activePeripheralMode.toString()} mode.");
                });
              },
              children: const [
                Padding(padding: EdgeInsets.symmetric(horizontal: 16), child: Text('Keyboard')),
                Padding(padding: EdgeInsets.symmetric(horizontal: 16), child: Text('Mouse')),
              ],
            ),
            const SizedBox(height: 15),
             ElevatedButton(
              onPressed: _requestPermissions,
              child: const Text('Request Permissions'),
            ),
            const SizedBox(height: 10),
            ElevatedButton(
              onPressed: _toggleAdvertising,
              style: ElevatedButton.styleFrom(
                backgroundColor: _isAdvertising ? Colors.redAccent : Colors.green,
              ),
              child: Text(_isAdvertising ? 'Stop Advertising ${_activePeripheralMode.toString().split('.').last}' : 'Start Advertising ${_activePeripheralMode.toString().split('.').last}'),
            ),
            const SizedBox(height: 20),
            if (_activePeripheralMode == PeripheralMode.keyboard) ...[
              TextField(
                controller: _keysController,
                decoration: const InputDecoration(
                  labelText: 'Keys to Send',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 10),
              ElevatedButton(
                onPressed: _sendKeys,
                child: const Text('Send Keys'),
              ),
            ] else if (_activePeripheralMode == PeripheralMode.mouse) ...[
              // Mouse controls 
              Wrap(
                spacing: 8.0, // gap between adjacent chips
                runSpacing: 4.0, // gap between lines
                children: <Widget>[
                  ElevatedButton(onPressed: () => _sendMouseMovement(dx: -10, dy: 0, dWheel: 0, buttons: _currentMouseButtons), child: const Text('Left')),
                  ElevatedButton(onPressed: () => _sendMouseMovement(dx: 10, dy: 0, dWheel: 0, buttons: _currentMouseButtons), child: const Text('Right')),
                  ElevatedButton(onPressed: () => _sendMouseMovement(dx: 0, dy: -10, dWheel: 0, buttons: _currentMouseButtons), child: const Text('Up')),
                  ElevatedButton(onPressed: () => _sendMouseMovement(dx: 0, dy: 10, dWheel: 0, buttons: _currentMouseButtons), child: const Text('Down')),
                  ElevatedButton(onPressed: () => _sendMouseMovement(dx: 0, dy: 0, dWheel: 10, buttons: _currentMouseButtons), child: const Text('Scroll Up')),
                  ElevatedButton(onPressed: () => _sendMouseMovement(dx: 0, dy: 0, dWheel: -10, buttons: _currentMouseButtons), child: const Text('Scroll Down')),
                ],
              ),
              const SizedBox(height: 10),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                children: [
                  Expanded(
                    child: ElevatedButton(
                      onPressed: () => _toggleMouseButton(1), // Left button (0x01)
                      style: ElevatedButton.styleFrom(backgroundColor: (_currentMouseButtons & 1) != 0 ? Colors.amber : null),
                      child: const Text('Left Btn'),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: ElevatedButton(
                      onPressed: () => _toggleMouseButton(2), // Right button (0x02)
                      style: ElevatedButton.styleFrom(backgroundColor: (_currentMouseButtons & 2) != 0 ? Colors.amber : null),
                      child: const Text('Right Btn'),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: ElevatedButton(
                      onPressed: () => _toggleMouseButton(4), // Middle button (0x04)
                      style: ElevatedButton.styleFrom(backgroundColor: (_currentMouseButtons & 4) != 0 ? Colors.amber : null),
                      child: const Text('Mid Btn'),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              Text("Note: Buttons are toggles. Click to press, click again to release. Movement commands use current button state.", style: Theme.of(context).textTheme.bodySmall)
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildLogSection() {
    return Card(
      elevation: 2,
      child: Padding(
        padding: const EdgeInsets.all(12.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text("Logs:", style: Theme.of(context).textTheme.titleSmall),
                IconButton(icon: Icon(Icons.clear_all), onPressed: () => setState(()=> _logMessages.clear()), tooltip: "Clear Logs",)
              ],
            ),
            const SizedBox(height: 8),
            Container(
              height: 150,
              decoration: BoxDecoration(
                border: Border.all(color: Colors.grey),
                borderRadius: BorderRadius.circular(4),
              ),
              child: ListView.builder(
                reverse: true, // Show newest logs first
                itemCount: _logMessages.length,
                itemBuilder: (context, index) {
                  return Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 8.0, vertical: 2.0),
                    child: Text(_logMessages[index], style: const TextStyle(fontSize: 12)),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}
