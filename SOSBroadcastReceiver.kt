package com.example.luna

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class SOSBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Check which action was sent in the broadcast
        val action = intent.action
        if (action == "SEND_SOS") {
            // Create an intent to start your SOS Activity
            val sosIntent = Intent(context, MainActivity::class.java)

            // Make sure to add the FLAG_ACTIVITY_NEW_TASK flag when starting the activity
            sosIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

            // Start the activity
            context.startActivity(sosIntent)
        }
    }
}


