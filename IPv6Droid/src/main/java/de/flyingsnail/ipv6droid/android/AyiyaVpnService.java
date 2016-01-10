/*
 * Copyright (c) 2015 Dr. Andreas Feldner.
 *
 *     This program is free software; you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation; either version 2 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License along
 *     with this program; if not, write to the Free Software Foundation, Inc.,
 *     51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Contact information and current version at http://www.flying-snail.de/IPv6Droid
 */

package de.flyingsnail.ipv6droid.android;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.VpnService;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.ayiya.TicConfiguration;
import de.flyingsnail.ipv6droid.ayiya.TicTunnel;

/**
 * The Android service controlling the VpnThread.
 * Created by pelzi on 15.08.13.
 */
public class AyiyaVpnService extends VpnService {

    private static final String TAG = AyiyaVpnService.class.getName();
    private static final String SESSION_NAME = AyiyaVpnService.class.getSimpleName();

    public static final String EXTRA_CACHED_TUNNEL = AyiyaVpnService.class.getName() + ".CACHED_TUNNEL";

    public static final String STATISTICS_INTERFACE = AyiyaVpnService.class.getPackage().getName() + ".Statistics";

    // the thread doing the work
    private VpnThread thread;

    // broadcast receivers
    private final ConnectivityReceiver connectivityReceiver = new ConnectivityReceiver();
    private final CommandReceiver commandReceiver = new CommandReceiver();

    /**
     * A pre-constructed notification builder for building user notifications.
     */
    private NotificationCompat.Builder notificationBuilder;

    /**
     * We're only ever displaying one notification, this is its ID.
     */
    private final int notificationID = 0xdeadbeef;


    @Override
    public void onCreate() {
        super.onCreate();
        this.notificationBuilder = new NotificationCompat.Builder(getApplicationContext())
                .setSmallIcon(R.drawable.ic_notification);

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // instantiate a notification builder


        Log.d(TAG, "received start command");
        if (thread == null || !thread.isAlive()) {
            // Build the configuration object from the saved shared preferences.
            SharedPreferences myPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            TicConfiguration ticConfiguration = loadTicConfiguration(myPreferences);
            RoutingConfiguration routingConfiguration = loadRoutingConfiguration(myPreferences);
            Log.d(TAG, "retrieved configuration");

            // register receivers of broadcasts
            registerLocalCommandReceiver();
            registerGlobalConnectivityReceiver();

            // Start a new session by creating a new thread.
            TicTunnel cachedTunnel = (TicTunnel)intent.getSerializableExtra(EXTRA_CACHED_TUNNEL);
            thread = new VpnThread(this, cachedTunnel, ticConfiguration, routingConfiguration, SESSION_NAME, startId);
            thread.start();
            Log.i(TAG, "VpnThread started");
        } else {
            Log.i(TAG, "VpnThread not started again - already running");
            Toast.makeText(getApplicationContext(),
                    R.string.vpnservice_already_running,
                    Toast.LENGTH_LONG).show();

        }
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Prepare destruction of VpnService");
        if (thread != null && thread.isAlive()) {
            thread.requestTunnelClose();
            notifyUserOfError(R.string.ayiyavpnservice_destroyed, new Exception());
        }
        unregisterGlobalConnectivityReceiver();
        unregisterLocalCommandReceiver();
    }

    @Override
    public void onRevoke() {
        Log.i(TAG, "VPN usage rights are being revoked - closing tunnel thread");
        if (thread != null && thread.isAlive()) {
            thread.requestTunnelClose();
            notifyUserOfError(R.string.ayiyavpnservice_revoked, new Exception());
        }
        super.onRevoke();
    }

    /**
     * Register the instance of connectivityReceiver for global CONNECTIVITY_ACTIONs.
     */
    private void registerGlobalConnectivityReceiver() {
        final IntentFilter intentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        getApplicationContext().registerReceiver(connectivityReceiver, intentFilter);
        Log.d(TAG, "registered CommandReceiver for global broadcasts");
    }

    /**
     * Revert registerGlobalConnectivityReceiver().
     */
    private void unregisterGlobalConnectivityReceiver() {
        getApplicationContext().unregisterReceiver(connectivityReceiver);
        Log.d(TAG, "un-registered CommandReceiver for global broadcasts");
    }

    /**
     * Register the instance of commandReceiver for local BC_* actions.
     */
    private void registerLocalCommandReceiver() {
        // setup the intent filter for status broadcasts and register receiver
        final IntentFilter intentFilter = new IntentFilter(MainActivity.BC_STOP);
        intentFilter.addAction(MainActivity.BC_STATUS_UPDATE);
        LocalBroadcastManager.getInstance(this).registerReceiver(commandReceiver,
                intentFilter);
        Log.d(TAG, "registered CommandReceiver for local broadcasts");
    }

    /**
     * Revert registerLocalCommandReceiver.
     */
    private void unregisterLocalCommandReceiver() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(commandReceiver);
        Log.d(TAG, "un-registered CommandReceiver for local broadcasts");
    }

    /**
     * Create a new instance of AyiyaVpnService.Builder. This method exists solely for VpnThread.
     * @return a new instance.
     */
    protected Builder createBuilder() {
        return new Builder();
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (STATISTICS_INTERFACE.equals(intent.getAction())) {
            Log.i(AyiyaVpnService.TAG, "Bind request to statistics interface received");
            return new StatisticsBinder();
        } else
            return super.onBind(intent);
    }

    /**
     * Generate a user notification with the supplied expection's cause as detail message.
     * @param resourceId the string resource supplying the notification title
     * @param e the Exception the cause of which is to be displayed
     */
    protected void notifyUserOfError(int resourceId, Throwable e) {
        notificationBuilder.setContentTitle(getString(resourceId));
        notificationBuilder.setContentText(
                String.valueOf(e.getClass())
        );
        notificationBuilder.setSubText(
                        String.valueOf(e.getLocalizedMessage())
        );

        Intent settingsIntent = new Intent(getApplicationContext(), SettingsActivity.class);
        // the following code is adopted directly from developer.android.com
        PendingIntent resultPendingIntent = PendingIntent.getActivity(
                getApplicationContext(),
                0,
                settingsIntent,
                PendingIntent.FLAG_ONE_SHOT);
        notificationBuilder.setContentIntent(resultPendingIntent);
        notificationBuilder.setAutoCancel(true);

        // provide the expanded layout
        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
        bigTextStyle.setBigContentTitle(getString(resourceId) + ": " + e.getClass());
        bigTextStyle.setSummaryText(e.getLocalizedMessage());
        notificationBuilder.setStyle(bigTextStyle);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        // mId allows you to update the notification later on.
        notificationManager.notify(notificationID, notificationBuilder.build());
    }

    /** Inner class to handle stop requests */
    private class CommandReceiver extends BroadcastReceiver {
        private CommandReceiver() {}

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(MainActivity.BC_STOP)) {
                Log.i(TAG, "Received explicit stop brodcast, will stop VPN Tread");
                thread.requestTunnelClose();
            } else if (action.equals(MainActivity.BC_STATUS_UPDATE)) {
                Log.i(TAG, "Someone requested a status report, will have one send");
                thread.reportStatus();
            }
        }
    }

    /**
     * Inner class to handle connectivity changes
     */
    public class ConnectivityReceiver extends BroadcastReceiver {
        private ConnectivityReceiver() {}

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                if (thread != null)
                    thread.onConnectivityChange (intent);
            }
        }
    }

    private TicConfiguration loadTicConfiguration(SharedPreferences myPreferences) {
        return new TicConfiguration(myPreferences.getString("tic_username", ""),
                myPreferences.getString("tic_password", ""),
                myPreferences.getString("tic_host", ""));
    }

    private RoutingConfiguration loadRoutingConfiguration(SharedPreferences myPreferences) {
        boolean workaround = myPreferences.getBoolean("routes_workaround", false) &&
                checkAndroidVersionForWorkaround();
        return new RoutingConfiguration(
                myPreferences.getBoolean("routes_default", true),
                myPreferences.getString("routes_specific", "::/0"),
                workaround,
                myPreferences.getBoolean("routes_setnameservers", false),
                myPreferences.getBoolean("routes_forcetunnel", false));
    }

    public static boolean checkAndroidVersionForWorkaround() {
        return (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT ||
                Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT_WATCH);
    }

    public class StatisticsBinder extends Binder {
        /**
         * Get statistics from the tunnel refreshing thread.
         * @return the Statistics, or null
         */
        public Statistics getStatistics() {
            return (thread == null || !thread.isTunnelUp()) ? null : thread.getStatistics();
        }
    }
}
