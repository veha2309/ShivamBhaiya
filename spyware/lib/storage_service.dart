import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';

class StorageService {
  static Future<String?> saveCompressedData(
      String fileName, List<Map<String, dynamic>> data) async {
    try {
      final dir = await getApplicationDocumentsDirectory();
      final file = File('${dir.path}/$fileName');

      final bytes = utf8.encode(jsonEncode(data, toEncodable: (item) {
        if (item is DateTime) return item.toIso8601String();
        return item.toString();
      }));

      await file.writeAsBytes(gzip.encode(bytes));
      debugPrint('Saved: ${file.absolute.path}');
      return file.absolute.path;
    } catch (e) {
      debugPrint('Error saving $fileName: $e');
      return null;
    }
  }
}
