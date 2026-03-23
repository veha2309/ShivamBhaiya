import 'package:flutter/material.dart';
import 'package:flutter_sms_inbox/flutter_sms_inbox.dart';
import 'storage_service.dart';

class SmsService {
  static Future<String?> fetchAndSave() async {
    try {
      final messages = await SmsQuery().querySms(
        kinds: [SmsQueryKind.inbox, SmsQueryKind.sent],
        count: 100,
      );
      final smsData = messages.map((m) => {
        'address': m.address,
        'body': m.body,
        'date': m.date?.toIso8601String(),
        'kind': m.kind?.toString(),
      }).toList();
      return await StorageService.saveCompressedData('sms_backup.gz', smsData);
    } catch (e) {
      debugPrint('Error fetching SMS: $e');
      return null;
    }
  }
}

class SmsScreen extends StatefulWidget {
  const SmsScreen({super.key});

  @override
  State<SmsScreen> createState() => _SmsScreenState();
}

class _SmsScreenState extends State<SmsScreen> {
  final SmsQuery _query = SmsQuery();
  List<SmsMessage> _messages = [];
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _fetchSms();
  }

  Future<void> _fetchSms() async {
    try {
      // Fetch inbox and sent messages
      List<SmsMessage> messages = await _query.querySms(
        kinds: [SmsQueryKind.inbox, SmsQueryKind.sent],
        count: 100, // Limiting to the first 100 for better performance
      );
      
      if (!mounted) return;
      setState(() {
        _messages = messages;
        _isLoading = false;
      });

      // Isolate SMS data and save to local storage
      final smsData = messages.map((m) => {
        'address': m.address,
        'body': m.body,
        'date': m.date?.toIso8601String(),
        'kind': m.kind?.toString(),
      }).toList();
      await StorageService.saveCompressedData('sms_backup.gz', smsData);
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isLoading = false;
      });
      debugPrint("Error fetching SMS: $e");
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('SMS Messages')),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _messages.isEmpty
              ? const Center(child: Text('No SMS messages found.'))
              : ListView.builder(
                  itemCount: _messages.length,
                  itemBuilder: (context, index) {
                    final msg = _messages[index];
                    return ListTile(
                      leading: const Icon(Icons.message),
                      title: Text(msg.address ?? 'Unknown sender'),
                      subtitle: Text(
                        msg.body ?? 'No content',
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                      trailing: Text(msg.date?.toString().split(' ').first ?? ''),
                    );
                  },
                ),
    );
  }
}