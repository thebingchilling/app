// SPDX-License-Identifier: GPL-3.0-only
package app.lychee.voice

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import helium314.keyboard.latin.R

/** A keyboard cannot ask for permissions itself: this invisible screen asks for the microphone. */
class MicPermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (hasPermission(this)) finish()
        else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        Toast.makeText(this, if (granted) R.string.lychee_mic_granted else R.string.lychee_mic_denied, Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        fun hasPermission(context: Context) =
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        fun request(context: Context) {
            context.startActivity(Intent(context, MicPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
