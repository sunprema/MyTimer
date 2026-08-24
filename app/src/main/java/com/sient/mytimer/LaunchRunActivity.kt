package com.sient.mytimer

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/**
 * Invisible trampoline: tiles can only launch activities in their own
 * package, so the tile's Run chip lands here and we forward to MyRun.
 */
class LaunchRunActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = packageManager.getLaunchIntentForPackage("com.sient.myrun")
        if (intent != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "MyRun is not installed", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
