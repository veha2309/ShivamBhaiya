# Spyware (Flutter Data Retrieval App)

A Flutter application designed to retrieve, compress, and manage device data, including SMS messages and contacts.

## 🚀 Features

### 1. Data Extraction
* **SMS Messages:** Retrieves both inbox and sent SMS messages using the `flutter_sms_inbox` package. Extracts sender addresses, message body, timestamps, and message types.
* **Contacts:** Fetches the device's saved contacts using the `flutter_contacts` package, including display names, phone numbers, and email addresses.

### 2. Local Storage & Compression
* **GZIP Compression:** Automatically isolates the extracted data into a structured JSON format and compresses it into a binary `.gz` format to drastically reduce file size.
* **Local Sandbox Storage:** Saves the compressed backup files (`contacts_backup.gz` and `sms_backup.gz`) directly to the device's external storage directory for easy access via file manager apps.

### 3. Permissions Management
* Utilizes `permission_handler` to automatically prompt the user for necessary sensitive runtime permissions (SMS, Contacts, and Phone) upon app startup.
* Gracefully handles edge cases, such as permanently denied permissions or empty emulator databases.

### 4. Background Upload & Networking 
* **Upload Service:** Initializes an upload service upon startup to handle data transmission.
* **Background Operations:** Integrates `background_downloader` and `dio` to reliably upload or manage files even when the app is in the background.
* **Network Awareness:** Uses `connectivity_plus` to verify active internet connections before attempting network operations.

## 🛠️ Tech Stack & Dependencies

* **Framework:** Flutter (Dart)
* **Permissions:** `permission_handler`
* **Device Data:** `flutter_contacts`, `flutter_sms_inbox`
* **Storage:** `path_provider`, `dart:io` (GZIP)
* **Networking:** `dio`, `connectivity_plus`, `background_downloader`

## 📱 How to Run

1. Ensure you have the Flutter SDK installed and an Android Emulator or physical device connected.
2. Clone the repository.
3. Run `flutter pub get` to install all dependencies.
4. Run `flutter run` to launch the app.

*Note: If testing on an Android Emulator, the SMS and Contacts databases will be empty by default. You will need to manually add a contact or send a mock SMS using the emulator controls to see data populate.*

---