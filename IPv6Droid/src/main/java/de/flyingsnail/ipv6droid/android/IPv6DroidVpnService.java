/*
 *
 *  * Copyright (c) 2022 Dr. Andreas Feldner.
 *  *
 *  *     This program is free software; you can redistribute it and/or modify
 *  *     it under the terms of the GNU General Public License as published by
 *  *     the Free Software Foundation; either version 2 of the License, or
 *  *     (at your option) any later version.
 *  *
 *  *     This program is distributed in the hope that it will be useful,
 *  *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  *     GNU General Public License for more details.
 *  *
 *  *     You should have received a copy of the GNU General Public License along
 *  *     with this program; if not, write to the Free Software Foundation, Inc.,
 *  *     51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *  *
 *  * Contact information and current version at http://www.flying-snail.de/IPv6Droid
 *
 *
 */

package de.flyingsnail.ipv6droid.android;

import static android.app.Notification.PRIORITY_LOW;
import static android.app.PendingIntent.FLAG_IMMUTABLE;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.android.statistics.Statistics;
import de.flyingsnail.ipv6droid.android.statusdetail.StatisticsActivity;
import de.flyingsnail.ipv6droid.android.vpnrun.VpnStatusReport;
import de.flyingsnail.ipv6droid.android.vpnrun.VpnThread;

/**
 * The Android service controlling the VpnThread.
 * Created by pelzi on 15.08.13.
 */
public class IPv6DroidVpnService extends VpnService implements UserNotificationCallback {

    private static final String TAG = IPv6DroidVpnService.class.getName();
    private static final String SESSION_NAME = IPv6DroidVpnService.class.getSimpleName();

    public static final String EXTRA_CACHED_TUNNELS = IPv6DroidVpnService.class.getName() + ".CACHED_TUNNEL";

    public static final String STATISTICS_INTERFACE = Objects.requireNonNull(IPv6DroidVpnService.class.getPackage()).getName() + ".Statistics";
    private static final String CHANNEL_ERRORS_ID = "deadbeef";
    private static final String CHANNEL_STATUS_ID = "42";

    // the thread doing the work
    private VpnThread thread;

    // broadcast receivers
    private final CommandReceiver commandReceiver = new CommandReceiver();
    private final StatusReceiver statusReceiver = new StatusReceiver();

    /**
     * A pre-constructed notification builder for building user notifications.
     */
    private NotificationCompat.Builder errorNotificationBuilder;
    /**
     * A pre-constructed notification builder for building the ongoing notification that the tunnel is running.
     */
    private NotificationCompat.Builder ongoingNotificationBuilder;

    /**
     * We're only ever displaying one error notification, this is its ID.
     */
    private static final int exceptionNotificationID = 0xdeadbeef;

    /**
     * The ID of the ongoing notification that our service is running
     */
    private static final int ongoingNotificationId = 0xaffe;

    private Handler handler;

    /**
     * A flag that keeps track if the VPN is inteded to run or not.
     */
    private boolean vpnShouldRun = false;
    private TunnelPersisting tunnelPersisting;
    private Tunnels cachedTunnels;
    private boolean errorNotification;
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    public IPv6DroidVpnService() {
        cachedTunnels = null;
    }


    @Override
    public void onCreate() {
        Log.i(TAG, "Instance about to be created");
        super.onCreate();

        handler = new Handler(getMainLooper());

        // create notification builders
        createNotificationChannels();
        errorNotificationBuilder = createNotificationBuilder(SettingsActivity.class, CHANNEL_ERRORS_ID);

        ongoingNotificationBuilder = createNotificationBuilder(StatisticsActivity.class, CHANNEL_STATUS_ID);
        ongoingNotificationBuilder.setContentTitle(getString(R.string.app_name));
        ongoingNotificationBuilder.setOngoing(true);
        ongoingNotificationBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        ongoingNotificationBuilder.setLocalOnly(true); // not interesting on connected devices

        // register receivers of broadcasts
        registerLocalCommandReceiver();

        // register receiver for status updates to update the notification status
        IntentFilter statusIntentFilter = new IntentFilter(VpnStatusReport.BC_STATUS);

        // Registers the StatusReceiver and its intent filter
        LocalBroadcastManager.getInstance(this).registerReceiver(statusReceiver,
                statusIntentFilter);

        // load persisted tunnels from file
        tunnelPersisting = new TunnelPersistingFile(getApplicationContext());
        try {
            cachedTunnels = tunnelPersisting.readTunnels();
        } catch (FileNotFoundException e) {
            Log.i(TAG, "no persisted tunnels information");
        } catch (IOException e) {
            Log.e(TAG, "Can't load persisted tunnels", e);
            Toast.makeText(this, R.string.vpnservice_toast_cache_unreadable, Toast.LENGTH_SHORT)
                    .show();
        }
    }

    @Override
    public synchronized int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "received start command");
        if (thread == null || !thread.isIntendedToRun()) {
            // become user visible
            displayOngoingNotification(null);

            // Build the configuration object from the saved shared preferences.
            SharedPreferences myPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            RoutingConfiguration routingConfiguration = loadRoutingConfiguration(myPreferences);
            Log.d(TAG, "retrieved configuration");

            // Read out the requested tunnels configuration from the Intent, if present.
            // This is necessary to support, because it might differ from the persisted tunnel set,
            // specifically, the user might have <em>selected<em> a different tunnel from the list.
            if (intent != null) {
                // Android's Parcel system doesn't handle subclasses well, so...
                Serializable serializable = intent.getSerializableExtra(EXTRA_CACHED_TUNNELS);
                if (serializable != null) {
                    cachedTunnels = new Tunnels(serializable);
                }
            }

            // Start a new session by creating a new thread.
            thread = new VpnThread(this, cachedTunnels, routingConfiguration, SESSION_NAME);
            startVpn();
        } else {
            Log.i(TAG, "VpnThread not started again - already running");
            Toast.makeText(getApplicationContext(),
                    R.string.vpnservice_already_running,
                    Toast.LENGTH_LONG).show();

        }
        return START_REDELIVER_INTENT;
    }

    /**
     * Generate a user notification with the supplied expection's cause as detail message.
     *
     * @param resourceId the string resource supplying the notification title
     * @param e          the Exception the cause of which is to be displayed
     */
    @Override
    public void notifyUserOfError(int resourceId, @NonNull Throwable e) {
        Log.d(IPv6DroidVpnService.TAG, "Notifying user of error", e);
        errorNotificationBuilder.setContentTitle(getString(resourceId));
        errorNotificationBuilder.setContentText(String.valueOf(e.getClass()));
        errorNotificationBuilder.setSubText(String.valueOf(e.getLocalizedMessage()));
        errorNotificationBuilder.setAutoCancel(true);
        errorNotificationBuilder.setWhen(new Date().getTime());

        // provide the expanded layout
        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
        bigTextStyle.setBigContentTitle(getString(resourceId));
        bigTextStyle.setSummaryText(String.valueOf(e.getClass()));
        bigTextStyle.bigText(e.getLocalizedMessage());
        errorNotificationBuilder.setStyle(bigTextStyle);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        // mId allows you to update the notification later on.
        notificationManager.notify(IPv6DroidVpnService.exceptionNotificationID, errorNotificationBuilder.build());
        errorNotification = true;
    }

    /**
     * Cancel an error notification, if currently active.
     */
    @Override
    public void notifyUserOfErrorCancel() {
        if (errorNotification) {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            // mId allows you to update the notification later on.
            notificationManager.cancel(IPv6DroidVpnService.exceptionNotificationID);
            errorNotification = false;
        }
    }

    @Override
    public void postToast(int resId, int duration) {

        handler.post(() -> Toast.makeText(this, resId, duration).show());
    }

    /**
     * Start the created and initialised VPN thread.
     */
    private synchronized void startVpn() {
        vpnShouldRun = true;
        thread.start();
        Log.i(TAG, "VpnThread started");
    }

    /**
     * Request the VPN thread to stop.
     */
    private synchronized void stopVpn() {
        vpnShouldRun = false;
        if (thread != null && thread.isIntendedToRun()) {
            Log.i(TAG, "stopVpn - requestTunnelClose on VpnThread");
            thread.requestTunnelClose();
        }
        thread = null;
    }

    /**
     * Callback before the service gets destroyed.
     */
    @Override
    public void onDestroy() {
        Log.i(TAG, "Prepare destruction of VpnService");
        stopVpn();
        notifyUserOfError(R.string.ayiyavpnservice_destroyed, new Exception(""));
        unregisterLocalCommandReceiver();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(statusReceiver);
        super.onDestroy();
    }

    /**
     * Callback when we loose the rights to run a VPN.
     */
    @Override
    public void onRevoke() {
        Log.i(TAG, "VPN usage rights are being revoked - closing tunnel thread");
        stopVpn();
        notifyUserOfError(R.string.ayiyavpnservice_revoked, new Exception(""));
        super.onRevoke();
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
     * Create a new instance of IPv6DroidVpnService.Builder. This method exists solely for VpnThread.
     *
     * @return a new instance.
     */
    public Builder createBuilder() {
        return new Builder();
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (STATISTICS_INTERFACE.equals(intent.getAction())) {
            Log.i(TAG, "Bind request to statistics interface received");
            return new StatisticsBinder();
        } else if (SERVICE_INTERFACE.equals(intent.getAction())) {
            Log.i(TAG, "Bind request to android.net.VpnService");
            IBinder superBinder = super.onBind(intent);
            Log.d(TAG, "super returned IBinder " + superBinder);
            return superBinder;
        } else
            return null;
    }

    /**
     * Create the notification channels for our two types of notifications
     */
    private void createNotificationChannels() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channelErrors = new NotificationChannel(
                    CHANNEL_ERRORS_ID,
                    getString(R.string.channel_errors_name),
                    NotificationManager.IMPORTANCE_HIGH);
            channelErrors.setDescription(getString(R.string.channel_errors_description));
            NotificationChannel channelStatus = new NotificationChannel(
                    CHANNEL_STATUS_ID,
                    getString(R.string.channel_status_name),
                    NotificationManager.IMPORTANCE_LOW);
            channelStatus.setDescription(getString(R.string.channel_status_description));
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channelErrors);
            notificationManager.createNotificationChannel(channelStatus);
        }
    }

    /**
     * Prepare errorNotificationBuilder to build a notification from this service. Called from the
     * two specific notification display helper methods.
     */
    private NotificationCompat.Builder createNotificationBuilder(Class<? extends AppCompatActivity> intentClass, String channelId) {
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(getApplicationContext(), channelId)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setPriority(PRIORITY_LOW);

        Intent settingsIntent = new Intent(this, intentClass);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addNextIntentWithParentStack(settingsIntent);

        notificationBuilder.setContentIntent(stackBuilder.getPendingIntent(0, FLAG_IMMUTABLE));
        return notificationBuilder;
    }

    /**
     * A helper method to initialize and display a notification on the notification drawer
     * @param statusReport a VpnStatusReport giving details about current VPN status
     */
    private void displayOngoingNotification(@Nullable VpnStatusReport statusReport) {
        Log.d(TAG, "Displaying/updating ongoing notification " + statusReport);

        if (statusReport != null)
            ongoingNotificationBuilder.setContentText(getResources().getString(statusReport.getActivity()));
        else
            ongoingNotificationBuilder.setContentText(getResources().getString(R.string.vpnservice_activity_wait));
        startForeground(ongoingNotificationId, ongoingNotificationBuilder.build());
    }

    /**
     * Inner class to handle stop requests
     */
    private class CommandReceiver extends BroadcastReceiver {
        private CommandReceiver() {
        }

        @Override
        public void onReceive(@NonNull Context context, @NonNull Intent intent) {
            String action = intent.getAction();
            if (thread != null && thread.isAlive()) {
                if (action != null && action.equals(MainActivity.BC_STOP)) {
                    Log.i(TAG, "Received explicit stop broadcast, will stop VPN Tread");
                    executor.submit(() -> {
                        Log.d(TAG, "async close thread starting");
                        stopVpn();
                    });
                    stopSelf(); // user command is the only event that corresponds to "the work is done"
                } else if (action != null && action.equals(MainActivity.BC_STATUS_UPDATE)) {
                    Log.i(TAG, "Someone requested a status report, will have one send");
                    thread.reportStatus();
                }
            } else if (vpnShouldRun) {
                Log.e(TAG, "IPv6DroidVpnService's thread is broken altough it should run");
            }
        }
    }

    private RoutingConfiguration loadRoutingConfiguration(SharedPreferences myPreferences) {
        return new RoutingConfiguration(
                myPreferences.getBoolean("routes_default", true),
                myPreferences.getString("routes_specific", "::/0"),
                myPreferences.getBoolean("routes_setnameservers", false),
                myPreferences.getBoolean("routes_forcetunnel", false));
    }

    public class StatisticsBinder extends Binder {
        /**
         * Get statistics from the tunnel refreshing thread.
         *
         * @return the Statistics, or null
         */
        public Statistics getStatistics() {
            return (thread == null ) ? null : thread.getStatistics();
        }
    }

    /**
     * Inner class to handle status updates
     */
    private class StatusReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            VpnStatusReport statusReport = (VpnStatusReport)intent.getSerializableExtra(VpnStatusReport.EDATA_STATUS_REPORT);
            Log.i(TAG, "received status update: " + statusReport);
            if (statusReport != null) {
                // update persistent notification
                displayOngoingNotification(statusReport);
                // write back persisted tunnels if tunnel works and list was changed
                if (statusReport.isTunnelProvedWorking()) {
                    notifyUserOfErrorCancel();

                    if (cachedTunnels == null || !cachedTunnels.equals(statusReport.getTunnels())) {
                        cachedTunnels = statusReport.getTunnels();
                        try {
                            if (cachedTunnels != null)
                                tunnelPersisting.writeTunnels(cachedTunnels);
                        } catch (IOException e) {
                            Log.e(TAG, "Couldn't write tunnels to file", e);
                        }
                    }
                }
            }
        }
    }
}