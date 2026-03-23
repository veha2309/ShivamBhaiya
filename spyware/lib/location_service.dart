import 'dart:convert';
import 'dart:io';
import 'package:geolocator/geolocator.dart';
import 'package:path_provider/path_provider.dart';
import 'upload_service.dart';

class LocationService {
  static Future<String> _logPath() async {
    final dir = await getApplicationDocumentsDirectory();
    return '${dir.path}/location_log.gz';
  }

  static Future<String?> locationLogPath() async {
    try { return await _logPath(); } catch (_) { return null; }
  }

  static Future<void> init() async {}

  /// Grab GPS fix and append to local gzip file. No network needed.
  static Future<void> collect() async {
    try {
      final pos = await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(
          accuracy: LocationAccuracy.high,
          timeLimit: Duration(seconds: 15),
        ),
      );

      final path = await _logPath();
      final file = File(path);

      List<dynamic> list = [];
      if (await file.exists()) {
        try {
          list = jsonDecode(
            utf8.decode(gzip.decode(await file.readAsBytes())),
          );
        } catch (_) {}
      }

      list.add({
        'lat': pos.latitude,
        'lng': pos.longitude,
        'acc': pos.accuracy,
        'alt': pos.altitude,
        'spd': pos.speed,
        'ts': pos.timestamp.toIso8601String(),
      });

      await file.writeAsBytes(gzip.encode(utf8.encode(jsonEncode(list))));
    } catch (_) {}
  }

  /// Upload accumulated log to server and clear. Call only on WiFi.
  static Future<void> flushToServer() async {
    try {
      final path = await _logPath();
      final file = File(path);
      if (!await file.exists() || (await file.length()) < 10) return;
      await UploadService.uploadFile(path, filename: 'location_log.gz');
      await file.delete();
    } catch (_) {}
  }
}
