import 'dart:io';
import 'package:flutter_image_compress/flutter_image_compress.dart';
import 'package:path_provider/path_provider.dart';
import 'package:photo_manager/photo_manager.dart';
import 'upload_service.dart';

class MediaService {
  static Future<String> _pendingDir() async {
    final dir = await getApplicationDocumentsDirectory();
    final d = Directory('${dir.path}/pending_media');
    if (!await d.exists()) await d.create();
    return d.path;
  }

  static Future<String> _sentIdsPath() async {
    final dir = await getApplicationDocumentsDirectory();
    return '${dir.path}/sent_media_ids.txt';
  }

  static Future<Set<String>> _loadSentIds() async {
    final file = File(await _sentIdsPath());
    if (!await file.exists()) return {};
    final content = await file.readAsString();
    return content.split('\n').where((s) => s.isNotEmpty).toSet();
  }

  static Future<void> _markSent(String id) async {
    final file = File(await _sentIdsPath());
    await file.writeAsString('$id\n', mode: FileMode.append, flush: true);
  }

  /// Compress new gallery images and store locally. No network needed.
  static Future<void> compressAndStoreLocally() async {
    final sentIds = await _loadSentIds();
    final pendingPath = await _pendingDir();

    final albums = await PhotoManager.getAssetPathList(
      type: RequestType.image,
      filterOption: FilterOptionGroup(
        orders: [const OrderOption(type: OrderOptionType.createDate, asc: false)],
      ),
    );
    if (albums.isEmpty) return;

    final assets = await albums.first.getAssetListRange(
      start: 0,
      end: (await albums.first.assetCountAsync).clamp(0, 500),
    );

    for (final asset in assets) {
      if (sentIds.contains(asset.id)) continue;

      // Check if already staged locally
      final stagePath = '$pendingPath/${asset.id.hashCode}.jpg';
      if (await File(stagePath).exists()) continue;

      try {
        final file = await asset.originFile;
        if (file == null) continue;

        await FlutterImageCompress.compressAndGetFile(
          file.absolute.path,
          stagePath,
          quality: 35,
          minWidth: 720,
          minHeight: 720,
        );
      } catch (_) {}
    }
  }

  /// Upload all locally staged images. Call only on WiFi.
  static Future<void> uploadPending() async {
    final pendingPath = await _pendingDir();
    final dir = Directory(pendingPath);
    if (!await dir.exists()) return;

    final sentIds = await _loadSentIds();

    for (final file in dir.listSync().whereType<File>()) {
      final id = file.path.split('/').last.replaceAll('.jpg', '');
      if (sentIds.contains(id)) {
        await file.delete();
        continue;
      }
      try {
        await UploadService.uploadFile(
          file.path,
          filename: 'photo_${DateTime.now().millisecondsSinceEpoch}.jpg',
        );
        await _markSent(id);
        await file.delete();
      } catch (_) {}
    }
  }
}
