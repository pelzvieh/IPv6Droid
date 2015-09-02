package de.flyingsnail.ipv6droid.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * This provides a callback function, asking MainActivity to start the VPN tunnel when it receives a boot completed
 * message.
 */
public class BootReceiver extends BroadcastReceiver {
    public BootReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences myPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED) &&
                myPrefs.getBoolean("autostart", false)) {
            Intent i = new Intent(context, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.putExtra(MainActivity.EXTRA_AUTOSTART, true);
            context.startActivity(i);
        }
    }
}
