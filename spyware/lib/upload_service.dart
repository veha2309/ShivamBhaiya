import 'package:dio/dio.dart';

const String _serverUrl = 'http://192.168.29.216:3000/upload';

class UploadService {
  static final _dio = Dio();

  static Future<void> uploadFile(String filePath, {String? filename}) async {
    try {
      final name = filename ?? filePath.split('/').last;
      await _dio.post(
        _serverUrl,
        data: FormData.fromMap({
          'file': await MultipartFile.fromFile(filePath, filename: name),
        }),
        options: Options(sendTimeout: const Duration(seconds: 60)),
      );
    } catch (_) {}
  }
}
