package com.andebugulin.nfcguard.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.andebugulin.nfcguard.data.AppLogger

/**
 * Device admin receiver for uninstall protection.
 *
 * When uninstall protection is enabled and the app is LOCKED,
 * this receiver prevents normal uninstallation via Android's
 * device administration APIs.
 */
class GuardianDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        AppLogger.log("DEVICE_ADMIN", "Device admin enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        AppLogger.log("DEVICE_ADMIN", "Device admin disabled")
    }
}
