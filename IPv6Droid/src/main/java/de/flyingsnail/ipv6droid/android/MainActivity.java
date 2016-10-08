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

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.net.VpnService;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.FileNotFoundException;
import java.io.IOException;

import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.android.googlesubscription.SubscribeTunnel;
import de.flyingsnail.ipv6droid.android.statusdetail.StatisticsActivity;
import de.flyingsnail.ipv6droid.ayiya.TicTunnel;

/**
 * Main activity as generated by Android Studio, plus Code to start the service when user clicks.
 */
public class MainActivity extends Activity {

    /**
     * The tag to use for logging
     */
    private static final String TAG = MainActivity.class.getName();
    private static final int REQUEST_START_VPN = 1;
    // private static final int REQUEST_SETTINGS = 2;
    private static final int REQUEST_STATISTICS = 3;
    public static final String TIC_USERNAME = "tic_username";
    public static final String TIC_PASSWORD = "tic_password";
    public static final String TIC_HOST = "tic_host";

    /** A TextView that presents in natural language, what is going on */
    private TextView activity;
    /** A ProgressBar that visualizes the progress of building the tunnel */
    private ProgressBar progress;
    /** An ImageView that visualizes the current status of the tunnel */
    private ImageView status;
    /** An additional start button that is shown if we're just waiting for the user to start */
    private Button redundantStartButton;
    /** The ListView that is going to list all available tunnels */
    private ListView tunnelListView;
    /** A TextView that presents the reason for the current status, if this represents a fault */
    private TextView causeView;
    /** The Tunnels object that holds the list of available tunnels plus the currently selected one */
    private Tunnels tunnels;
/*    private TicTunnel selectedTunnel;
    private List<TicTunnel> availableTunnels;*/
    //@todo maintaining the event list in an Activity is nonsense, as it would receive events only when visible
    //private Deque<VpnStatusReport> lastEvents;
    //private final int EVENT_LENGTH=10;

    /**
     * The Action name for a vpn stop broadcast intent.
     */
    public static final String BC_STOP = MainActivity.class.getName() + ".STOP";

    /**
     * The Action name for a status update request broadcast.
     */
    public static final String BC_STATUS_UPDATE = MainActivity.class.getName() + ".STATUS_REQUEST";

    /**
     * The receiver for status broadcasts from the AyiyaVpnService.
     */
    private StatusReceiver statusReceiver;

    /**
     * The menuitem for refreshing the tunnel list. This will be enabled or disabled depending on
     * the VPN status.
     */
    private MenuItem refreshTunnelMenuItem;
    /**
     * An object implementing TunnelPersisting, i.e. a Binder-based DAO to persistent tunnel storage.
     */
    private TunnelPersisting tunnelPersisting;


    /**
     * Overridden method from activity, initialises the activity and restores any previously saved
     * state information
     * @param savedInstanceState the Bundle to which state was saved previously.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        PreferenceManager.setDefaultValues(getApplicationContext(), R.xml.preferences, false);
        SharedPreferences myPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // init handles to GUI elements
        activity = (TextView) findViewById(R.id.statusText);
        progress = (ProgressBar) findViewById(R.id.progressBar);
        status = (ImageView) findViewById(R.id.statusImage);
        redundantStartButton = (Button) findViewById(R.id.redundant_start_button);
        tunnelListView = (ListView) findViewById(R.id.tunnelList);
        causeView = (TextView) findViewById(R.id.cause);
        tunnels = new Tunnels();
        ArrayAdapter<TicTunnel> adapter = new ArrayAdapter<TicTunnel>(MainActivity.this,
                R.layout.tunnellist_template);
        adapter.addAll(tunnels);
        tunnelListView.setAdapter(adapter);
        if (statusReceiver == null)
            statusReceiver = new StatusReceiver();
        //if (lastEvents == null)
        //    lastEvents = new LinkedBlockingDeque<>(EVENT_LENGTH);

        // setup the intent filter for status broadcasts
        IntentFilter statusIntentFilter = new IntentFilter(VpnStatusReport.BC_STATUS);

        // Registers the StatusReceiver and its intent filter
        LocalBroadcastManager.getInstance(this).registerReceiver(statusReceiver,
                statusIntentFilter);

        // load tunnels list persisted from last session
        tunnelPersisting = new TunnelPersistingFile(getApplicationContext());
        try {
            tunnels.setAll(tunnelPersisting.readTunnels());
        } catch (FileNotFoundException e) {
            Log.i(TAG, "Could not load persisted tunnels - probably first invocation", e);
        } catch (IOException e) {
            Log.e(TAG, "Could not load persisted tunnels", e);
        }
        statusReceiver.updateUi();

        redirectIfRequired();

        requestStatus();
    }

    /**
     * This overriden method is called before the instance gets destroyed.
     */
    @Override
    protected void onDestroy() {
        // switch off ui updates
        LocalBroadcastManager.getInstance(this).unregisterReceiver(statusReceiver);
        statusReceiver = null;
        super.onDestroy();
    }

    /**
     * This overridden method gets called when the menu should be created.
     * @param menu the Menu to inflate to
     * @return a boolean, always true
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        refreshTunnelMenuItem = menu.findItem(R.id.action_tic_reload);
        return true;
    }

    /**
     * Start the system-managed setup of VPN. This is the overloaded method suitable as a
     * declared callback in layout xml definitions.
     * @param clickedView is the View where the click occurred. This parameter is not used.
     */
    public void startVPN(View clickedView) {
        // update selected tunnel
        int checkedItem = tunnelListView.getCheckedItemPosition();
        if (checkedItem != AdapterView.INVALID_POSITION) {
            tunnels.setActiveTunnel((TicTunnel) tunnelListView.getItemAtPosition(checkedItem));
        }

        // Start system-managed intent for VPN
        Intent systemVpnIntent = VpnService.prepare(clickedView == null ?
                getApplicationContext() : clickedView.getContext());
        if (systemVpnIntent != null) {
            startActivityForResult(systemVpnIntent, REQUEST_START_VPN);
        } else {
            onActivityResult (REQUEST_START_VPN, RESULT_OK, null);
        }
    }

    /**
     * This overriden method restores its state from a Bundle.
     * @param savedInstanceState the Bundle containing the saved state.
     */
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        requestStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // @todo writing back the tunnels list should not depend on a visible Activity getting paused.
        if (!tunnels.isEmpty() && tunnels.isTunnelActive() && statusReceiver.isTunnelProven()) {
            Log.i (TAG, "We have an updated tunnel list and will write it back to cache");
            try {
                tunnelPersisting.writeTunnels(tunnels);
            } catch (Exception e) {
                Log.e(TAG, "Could not write tunnel information to private file", e);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        redirectIfRequired();

        // update status
        requestStatus();
    }

    /**
     * Launches the subscription/setup intent if current setup is not operationable.
     */
    private void redirectIfRequired() {
        // check login configuration and start first time setup activity if not yet set.
        SharedPreferences myPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        if (myPreferences.getString(TIC_USERNAME, "").isEmpty() ||
                myPreferences.getString(TIC_PASSWORD, "").isEmpty() ||
                myPreferences.getString(TIC_HOST, "").isEmpty() ||
                (myPreferences.getString(TIC_USERNAME, "").equals("<googlesubscription>")
                        && !checkCachedTunnelAvailability())) {
            openSubscriptionOverview();
        }
    }

    /**
     * Test if there are cached tunnels, and if the cached tunnels are expired today.
     * @return true if there are non-expired tunnels, false if there are no tunnels, or some expired.
     */
    private boolean checkCachedTunnelAvailability() {
        if (tunnels.size() == 0) {
            Log.i(TAG, "No tunnels are cached");
            return false;
        }
        for (TicTunnel tunnel: tunnels) {
            if (!tunnel.isEnabled()) {
                Log.i(TAG, String.format("Tunnel %s (%s) is expired", tunnel.getTunnelName(), tunnel.getTunnelId()));
                return false; // one tunnel is expired or disabled
            }
        }
        Log.i(TAG, "Valid tunnels are in cache");
        return true; // all tunnels are enabled
    }

    /**
     * Start the settings setup activity via Intent.
     */
    private void openSettings () {
        Intent settingsIntent = new Intent(this, SettingsActivity.class);
        startActivity(settingsIntent);
    }

    private void openSubscriptionOverview () {
        Intent setupIntent = new Intent(this, SubscribeTunnel.class);
        startActivity(setupIntent);
    }



    /**
     * Start the detached statistics view
     */
    public void openStatistics () {
        // Start system-managed intent for VPN
        Intent settingsIntent = new Intent(this, StatisticsActivity.class);
        startActivityForResult(settingsIntent, REQUEST_STATISTICS);
    }

    /**
     * Stop the VPN service/thread.
     */
    public void stopVPN () {
        onPause(); // functionally equivalent to a pause of our activity.
        Intent statusBroadcast = new Intent(BC_STOP);
        // Broadcast locally
        LocalBroadcastManager.getInstance(this).sendBroadcast(statusBroadcast);
    }

    /**
     * Broadcast a status update request. If there's a AyiyaVpnService out there, it will respond
     * with a standard status message.
     */
    private void requestStatus () {
        Intent statusBroadcast = new Intent(BC_STATUS_UPDATE);
        // Broadcast locally
        LocalBroadcastManager.getInstance(this).sendBroadcast(statusBroadcast);
    }

    /**
     * This is a callback that is going to be called by Android if the user agreed to starting
     * a VPN by this app.
     *
     * @param requestCode an int that should be the magic number REQUEST_START_VPN
     * @param resultCode an int that should be the magic number RESULT_OK
     * @param data an Intent
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_START_VPN:
                if (resultCode == RESULT_OK) {
                    Intent intent = new Intent(this, AyiyaVpnService.class);
                    if (tunnels.isTunnelActive()) {
                        // Android's Parcel system doesn't handle subclasses well, so...
                        intent.putExtra(AyiyaVpnService.EXTRA_CACHED_TUNNELS, tunnels.getAndroidSerializable());
                    }
                    startService(intent);
                }
                break;
        }
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_connect:
                startVPN(item.getActionView());
                return true;

            case R.id.action_disconnect:
                stopVPN();
                return true;

            case R.id.action_settings:
                openSettings();
                return true;

            case R.id.action_help:
                openHelp();
                return true;

            case R.id.action_tic_reload:
                forceTunnelReload(item.getActionView());
                return true;

            case R.id.action_subscribe:
                openSubscriptionOverview();
                return true;

            case R.id.action_show_statistics:
                openStatistics();
                return true;

            default:
                return false;
        }
    }

    private void openHelp() {
        Intent helpIntent = new Intent(Intent.ACTION_VIEW);
        helpIntent.setDataAndType(Uri.parse("https://github.com/pelzvieh/IPv6Droid/wiki"), "text/html");
        helpIntent.addCategory(Intent.CATEGORY_BROWSABLE);
        startActivity(helpIntent);
    }

    private void forceTunnelReload(View clickedView) {
        int checked = tunnelListView.getCheckedItemPosition();
        if (checked != AdapterView.INVALID_POSITION)
            tunnelListView.setItemChecked(checked, false);
        tunnels.clear();
        ((ArrayAdapter<TicTunnel>)(tunnelListView.getAdapter())).notifyDataSetChanged();
        try {
            tunnelPersisting.writeTunnels(tunnels);
        } catch (IOException e) {
            Log.wtf(TAG, "Could not write empty tunnel list to persistent configuration", e);
        }
        startVPN(clickedView);
    }

    /** Inner class to handle status updates */
    private class StatusReceiver extends BroadcastReceiver {
        /** The last received status report - including a updatedTunnelList if that was read for that update */
        private VpnStatusReport statusReport = new VpnStatusReport(null);

        public boolean isTunnelProven() {
            return statusReport.isTunnelProvedWorking();
        }

        private void updateUi () {
            int imageRes = R.drawable.off;
            VpnStatusReport.Status status = statusReport.getStatus();
            Log.i(TAG, "received status update: " + String.valueOf(statusReport));

            switch (status) {
                case Connected:
                    imageRes = R.drawable.transmitting;
                    break;
                case Idle:
                    imageRes = R.drawable.off;
                    break;
                case Connecting:
                    imageRes = R.drawable.pending;
                    break;
                case Disturbed:
                    imageRes = R.drawable.disturbed;
                    break;
            }
            MainActivity.this.status.setImageResource(imageRes);
            if (statusReport.getProgressPerCent() > 0) {
                MainActivity.this.progress.setIndeterminate(false);
                MainActivity.this.progress.setProgress(statusReport.getProgressPerCent());
            } else
                MainActivity.this.progress.setIndeterminate(true);

            if (status == VpnStatusReport.Status.Idle) {
                redundantStartButton.setVisibility(View.VISIBLE);
                MainActivity.this.progress.setVisibility(View.INVISIBLE);
                MainActivity.this.activity.setVisibility(View.INVISIBLE);
            } else {
                redundantStartButton.setVisibility(View.INVISIBLE);
                MainActivity.this.progress.setVisibility(View.VISIBLE);
                MainActivity.this.activity.setVisibility(View.VISIBLE);
            }
            tunnelListView.setEnabled(status == VpnStatusReport.Status.Idle);
            if (refreshTunnelMenuItem != null)
                refreshTunnelMenuItem.setEnabled(status == VpnStatusReport.Status.Idle);

            // show activity text
            if (statusReport.getActivity() != 0)
                MainActivity.this.activity.setText(getResources().getString(statusReport.getActivity()));

            // read tunnel information, if updated
            if (statusReport.getTunnels() != null) {
                tunnels.setAll(statusReport.getTunnels());
            }

            // show tunnel information
            if (!tunnels.isEmpty()) {
                Log.d(TAG, "Tunnels are set");

                ArrayAdapter<TicTunnel> tunnelAdapter = (ArrayAdapter<TicTunnel>)(tunnelListView.getAdapter());
                tunnelAdapter.clear();
                tunnelAdapter.addAll(tunnels);
                tunnelAdapter.notifyDataSetChanged();

                int position = tunnels.indexOf(tunnels.getActiveTunnel());
                if (position >= 0)
                  tunnelListView.setItemChecked(position, true);

                tunnelListView.setVisibility(View.VISIBLE);
            } else {
                Log.d(TAG, "No tunnels are set");
                tunnelListView.setVisibility(View.INVISIBLE);
            }
            Throwable cause = statusReport.getCause();
            if (cause != null && cause.getLocalizedMessage() != null) {
                causeView.setText(cause.getLocalizedMessage());
                causeView.setVisibility(View.VISIBLE);
            } else
                causeView.setVisibility(View.INVISIBLE);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            statusReport = (VpnStatusReport)intent.getSerializableExtra(VpnStatusReport.EDATA_STATUS_REPORT);
            /*if (lastEvents.size() >= EVENT_LENGTH)
                lastEvents.pollLast();
            lastEvents.push(statusReport);
            */
            updateUi();
        }
    }
}
