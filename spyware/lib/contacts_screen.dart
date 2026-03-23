import 'package:flutter/material.dart';
import 'package:flutter_contacts/flutter_contacts.dart';
import 'storage_service.dart';

class ContactsService {
  static Future<String?> fetchAndSave() async {
    try {
      final granted = await FlutterContacts.requestPermission(readonly: true);
      if (!granted) return null;
      final contacts = await FlutterContacts.getContacts(
        withProperties: true,
        withPhoto: false,
      );
      final contactsData = contacts.map((c) => {
        'id': c.id,
        'displayName': c.displayName,
        'phones': c.phones.map((p) => p.number).toList(),
        'emails': c.emails.map((e) => e.address).toList(),
      }).toList();
      return await StorageService.saveCompressedData('contacts_backup.gz', contactsData);
    } catch (e) {
      debugPrint('Error fetching contacts: $e');
      return null;
    }
  }
}

class ContactsScreen extends StatefulWidget {
  const ContactsScreen({super.key});

  @override
  State<ContactsScreen> createState() => _ContactsScreenState();
}

class _ContactsScreenState extends State<ContactsScreen> {
  List<Contact> _contacts = [];
  bool _isLoading = true;
  bool _permissionDenied = false;

  @override
  void initState() {
    super.initState();
    _fetchContacts();
  }

  Future<void> _fetchContacts() async {
    try {
      final granted = await FlutterContacts.requestPermission(readonly: true);
      if (!granted) {
        if (!mounted) return;
        setState(() {
          _isLoading = false;
          _permissionDenied = true;
        });
        return;
      }
      List<Contact> contacts = await FlutterContacts.getContacts(
        withProperties: true,
        withPhoto: false,
      );
      
      if (!mounted) return;
      setState(() {
        _contacts = contacts;
        _isLoading = false;
      });

      // Isolate contact data and save to local storage
      final contactsData = contacts.map((c) => {
        'id': c.id,
        'displayName': c.displayName,
        'phones': c.phones.map((p) => p.number).toList(),
        'emails': c.emails.map((e) => e.address).toList(),
      }).toList();
      await StorageService.saveCompressedData('contacts_backup.gz', contactsData);
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isLoading = false;
      });
      debugPrint("Error fetching contacts: $e");
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Contacts')),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _permissionDenied
              ? const Center(child: Text('Permission to read contacts was denied.'))
              : _contacts.isEmpty
                  ? const Center(child: Text('No contacts found. Try adding some to the device.'))
                  : ListView.builder(
                      itemCount: _contacts.length,
                      itemBuilder: (context, index) {
                        final contact = _contacts[index];
                        final phone = contact.phones.isNotEmpty
                            ? contact.phones.first.number
                            : 'No phone number';
                        return ListTile(
                          leading: const CircleAvatar(child: Icon(Icons.person)),
                          title: Text(contact.displayName),
                          subtitle: Text(phone),
                        );
                      },
                    ),
    );
  }
}