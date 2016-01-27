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

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.android.statusdetail.StatisticsActivity;
import de.flyingsnail.ipv6droid.ayiya.TicTunnel;

/**
 * Main activity as generated by Android Studio, plus Code to start the service when user clicks.
 */
public class MainActivity extends Activity {

    private static final String TAG = MainActivity.class.getName();
    private static final int REQUEST_START_VPN = 1;
    private static final int REQUEST_SETTINGS = 2;
    private static final int REQUEST_STATISTICS = 3;
    private static final String FILE_LAST_TUNNEL = "last_tunnel";
    public static final String EXTRA_AUTOSTART = "AUTOSTART";
    private TextView activity;
    private ProgressBar progress;
    private ImageView status;
    private Button redundantStartButton;
    private ListView tunnelListView;
    private TextView causeView;
    private TicTunnel selectedTunnel;
    private List<TicTunnel> availableTunnels;
    //@todo maintaining the event list in an Activity is nonsense, as it would receive events only when visible
    //private Deque<VpnStatusReport> lastEvents;
    private final int EVENT_LENGTH=10;

    /**
     * The Action name for a vpn stop broadcast intent.
     */
    public static final String BC_STOP = MainActivity.class.getName() + ".STOP";

    /**
     * The Action name for a status update request broadcast.
     */
    public static final String BC_STATUS_UPDATE = MainActivity.class.getName() + ".STATUS_REQUEST";
    private StatusReceiver statusReceiver;
    private MenuItem refreshTunnelMenuItem;
    private boolean autostart = false;

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
        flushTunnelLists();
        if (statusReceiver == null)
            statusReceiver = new StatusReceiver();
        //if (lastEvents == null)
        //    lastEvents = new LinkedBlockingDeque<>(EVENT_LENGTH);

        // setup the intent filter for status broadcasts
        IntentFilter statusIntentFilter = new IntentFilter(VpnStatusReport.BC_STATUS);

        // Registers the StatusReceiver and its intent filter
        LocalBroadcastManager.getInstance(this).registerReceiver(statusReceiver,
                statusIntentFilter);

        autostart = myPreferences.getBoolean("autostart", false);
        // check login configuration and start Settings if not yet set.
        if (myPreferences.getString("tic_username", "").isEmpty() ||
                myPreferences.getString("tic_password", "").isEmpty() ||
                myPreferences.getString("tic_host", "").isEmpty()) {
            openSettings();
        }

        loadPersistedTunnel();
        statusReceiver.updateUi();
        requestStatus();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = getIntent();
        boolean onBoot = intent.getBooleanExtra(MainActivity.EXTRA_AUTOSTART, false);
        if (onBoot && autostart) {
            startVPN(null);
            finish(); // close this Activity, as it was opened automatically at boot
        }
    }

    @Override
    protected void onDestroy() {
        // switch off ui updates
        LocalBroadcastManager.getInstance(this).unregisterReceiver(statusReceiver);
        statusReceiver = null;
        super.onDestroy();
    }

    private void flushTunnelLists() {
        availableTunnels = new ArrayList<TicTunnel>();
        selectedTunnel = null;
        tunnelListView.setAdapter(new ArrayAdapter<TicTunnel>(MainActivity.this,
                        R.layout.tunnellist_template,
                        availableTunnels)
        );
    }

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
            selectedTunnel = (TicTunnel) tunnelListView.getItemAtPosition(checkedItem);
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

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        requestStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (availableTunnels != null && !availableTunnels.isEmpty() && selectedTunnel != null && statusReceiver.isTunnelProven()) {
            Log.i (TAG, "We have an updated tunnel list and will write it back to cache");
            if (availableTunnels.contains(selectedTunnel)) {
                // we have a tunnel that should work
                writePersistedTunnel ();
            } else
                Log.e (TAG, "Inconsistent data from statusReceiver: active tunnel not in tunnel list - aborting write back");
        }
    }

    /**
     * Start the system-managed setup of VPN
     */
    public void openSettings () {
        // Start system-managed intent for VPN
        Intent settingsIntent = new Intent(this, SettingsActivity.class);
        startActivityForResult(settingsIntent, REQUEST_SETTINGS);
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_START_VPN:
                if (resultCode == RESULT_OK) {
                    Intent intent = new Intent(this, AyiyaVpnService.class);
                    if (selectedTunnel != null)
                        intent.putExtra(AyiyaVpnService.EXTRA_CACHED_TUNNEL, selectedTunnel);
                    startService(intent);
                }
                break;
        }
    }

    /** Read from a private file. If no such file exists, leave current list and selection untouched */
    private void loadPersistedTunnel() {
        TicTunnel tunnel;
        List<TicTunnel> cachedTunnels;
        try {
            // open private file
            InputStream is = openFileInput(FILE_LAST_TUNNEL);
            ObjectInputStream os = new ObjectInputStream(is);
            //noinspection unchecked
            cachedTunnels = (List<TicTunnel>)os.readObject();
            int selected = os.readInt();
            tunnel = cachedTunnels.get(selected);
        } catch (Exception e) {
            Log.e(TAG, "Could not retrieve saved state of TicTunnel", e);
            return;
        }

        // update MainActivities fields
        selectedTunnel = tunnel;
        availableTunnels = cachedTunnels;
    }

    /** Write to a private file. Format is: ArrayList&lt;TicTunnel&gt; tunnels; int selected */
    private void writePersistedTunnel() {
        try {
            OutputStream fs = openFileOutput(FILE_LAST_TUNNEL, MODE_PRIVATE);
            ObjectOutputStream os = new ObjectOutputStream(fs);
            os.writeObject(availableTunnels);
            os.writeInt(availableTunnels.indexOf(selectedTunnel));
            os.close();
            fs.close();
        } catch (IOException e) {
            Log.e(TAG, "Could not write tunnel information to private file", e);
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
        flushTunnelLists();
        startVPN(clickedView);
    }

    /** Inner class to handle status updates */
    private class StatusReceiver extends BroadcastReceiver {
        /** The last received status report - including a updatedTunnelList if that was read for that update */
        private VpnStatusReport statusReport = new VpnStatusReport(null);

        private StatusReceiver() {
            updateUi();
        }

        public boolean isTunnelProven() {
            return statusReport.isTunnelProvedWorking();
        }

        private void updateUi () {
            int imageRes = R.drawable.off;
            VpnStatusReport.Status status = statusReport.getStatus();
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
            if (statusReport.getTicTunnelList() != null)
                availableTunnels = statusReport.getTicTunnelList();

            // deal with null here to avoid nasty checks everywhere else...
            if (statusReport.getActiveTunnel() != null)
                selectedTunnel = statusReport.getActiveTunnel();

            // show tunnel information
            // @todo implementation is too cheap - no internationalization, etc. Necessary to generate custom Adapter.
            tunnelListView.setAdapter(new ArrayAdapter<TicTunnel>(MainActivity.this,
                            R.layout.tunnellist_template,
                            availableTunnels)
            );
            if (!availableTunnels.isEmpty()) {
                tunnelListView.setVisibility(View.VISIBLE);

                int position = availableTunnels.indexOf(selectedTunnel);
                if (position >= 0)
                  tunnelListView.setItemChecked(position, true);
            } else {
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
