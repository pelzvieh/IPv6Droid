package de.flyingsnail.ipv6droid.android;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.ayiya.TicConfiguration;

/**
 * Created by pelzi on 15.08.13.
 * (c) Dr. Andreas Feldner, see license file.
 */
public class AiccuVpnService extends VpnService {

    private static final String TAG = AiccuVpnService.class.getName();
    private static final String SESSION_NAME = AiccuVpnService.class.getSimpleName();

    private PendingIntent configureIntent;

    private VpnThread thread;


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "received start command");
        if (thread == null || !thread.isAlive()) {
            // Build the configuration object from the saved shared preferences.
            SharedPreferences myPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            TicConfiguration ticConfiguration = loadTicConfiguration(myPreferences);
            RoutingConfiguration routingConfiguration = loadRoutingConfiguration(myPreferences);
            Log.d(TAG, "retrieved configuration");

            // setup the intent filter for status broadcasts and register receiver
            IntentFilter statusIntentFilter = new IntentFilter(MainActivity.BC_STOP);
            CommandReceiver commandReceiver = new CommandReceiver();
            LocalBroadcastManager.getInstance(this).registerReceiver(commandReceiver,
                    statusIntentFilter);
            Log.d(TAG,"registered CommandReceiver");

            // Start a new session by creating a new thread.
            thread = new VpnThread(this, ticConfiguration, routingConfiguration, SESSION_NAME);
            thread.start();
            Log.i(TAG, "VpnThread started");
        } else {
            Log.i(TAG, "VpnThread not started again - already running");
            Toast.makeText(getApplicationContext(),
                    R.id.vpnservice_already_running,
                    Toast.LENGTH_LONG);
        }
        return START_STICKY;
    }


    /**
     * Create a new instance of AiccuVpnService.Builder. This method exists solely for VpnThread.
     * @return a new instance.
     */
    protected Builder createBuilder() {
        return new Builder();
    }

    /** Inner class to handle status updates */
    private class CommandReceiver extends BroadcastReceiver {
        private CommandReceiver() {}

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(MainActivity.BC_STOP)) {
                Log.i(TAG, "Received explicit stop brodcast, will stop VPN Tread");
                thread.interrupt();
            }
        }
    }


    private TicConfiguration loadTicConfiguration(SharedPreferences myPreferences) {
        return new TicConfiguration(myPreferences.getString("tic_username", ""),
                myPreferences.getString("tic_password", ""),
                myPreferences.getString("tic_host", "tic.sixxs.net"));
    }

    private RoutingConfiguration loadRoutingConfiguration(SharedPreferences myPreferences) {
        return new RoutingConfiguration(
                myPreferences.getBoolean("routes_default", true),
                myPreferences.getString("routes_specific", "::/0"));
    }

    @Override
    public void onDestroy() {
        if (thread != null && !thread.isInterrupted()) {
            thread.interrupt();
        }
    }
}
