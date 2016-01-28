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

package de.flyingsnail.ipv6droid.android.statusdetail;

import android.app.Fragment;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.android.AyiyaVpnService;
import de.flyingsnail.ipv6droid.android.statistics.Statistics;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link StatisticsFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
//@TargetApi(18)
public class StatisticsFragment extends Fragment implements ServiceConnection {
    private static final String ARG_STATISTICS_BINDER = "StatisticsBinder";
    private static final String TAG = StatisticsFragment.class.getName();


    private AyiyaVpnService.StatisticsBinder statisticsBinder;

    private TextView bytesReceivedView;
    private TextView bytesTransmittedView;
    private TextView mtuView;
    private TextView packetsReceivedView;
    private TextView packetsTransmittedView;
    private TextView bytesPerBurstTransmittedView;
    private TextView bytesPerBurstReceivedView;
    private TextView packetsPerBurstTransmittedView;
    private TextView packetsPerBurstReceivedView;
    private TextView timeSpanPerBurstTransmittedView;
    private TextView timeSpanPerBurstReceivedView;
    private TextView timeLapseBetweenBurstsTransmittedView;
    private TextView timeLapseBetweenBurstsReceivedView;
    private TextView brokerIPv4View;
    private TextView brokerIPv6View;
    private TextView myIPv4View;
    private TextView myIPv6View;
    private TextView nativeRoutesView;
    private TextView vpnRoutesView;
    private TextView nativeDnsView;
    private TextView vpnDnsView;
    private TextView timestampView;
    private TextView isRoutedView;
    private ScheduledThreadPoolExecutor executor;
    private DateFormat timestampFormatter;
    private Future<?> updaterFuture;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment StatisticsFragment.
     */
    public static StatisticsFragment newInstance() {
        return new StatisticsFragment();
    }

    public StatisticsFragment() {
        updaterFuture = null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate started");
        super.onCreate(savedInstanceState);
        // create scheduled executor
        executor = new ScheduledThreadPoolExecutor(1);
        // bind to AyiyaVpnService for statistics
        bindToStatistics();
        timestampFormatter = android.text.format.DateFormat.getTimeFormat(getActivity());
        Log.i(TAG, "Creation successful");
    }

    /**
     * request to bind to the statistics interface of the AyiyaVpnService.
     */
    private void bindToStatistics() {
        Intent intent = new Intent(getActivity(), AyiyaVpnService.class);
        intent.setAction(AyiyaVpnService.STATISTICS_INTERFACE);
        if (!getActivity().getApplicationContext().bindService(intent, this, 0))
            Log.e(StatisticsFragment.TAG, "Bind request to statistics interface failed");
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy started");
        getActivity().getApplicationContext().unbindService(this);

        if (updaterFuture != null) {
            updaterFuture.cancel(true);
            updaterFuture = null;
        }
        executor.shutdownNow();
        executor = null;
        super.onDestroy();
        Log.i(TAG, "Destroyed");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView started");
        // Inflate the layout for this fragment
        View myView = inflater.inflate(R.layout.fragment_statistics, container, false);

        bytesReceivedView = (TextView)myView.findViewById(R.id.statistics_bytes_received);
        bytesTransmittedView = (TextView)myView.findViewById(R.id.statistics_bytes_transmitted);
        packetsReceivedView = (TextView)myView.findViewById(R.id.statistics_packets_received);
        packetsTransmittedView = (TextView)myView.findViewById(R.id.statistics_packets_transmitted);
        bytesPerBurstReceivedView = (TextView)myView.findViewById(R.id.statistics_bytes_per_burst_received);
        bytesPerBurstTransmittedView = (TextView)myView.findViewById(R.id.statistics_bytes_per_burst_transmitted);
        packetsPerBurstReceivedView = (TextView)myView.findViewById(R.id.statistics_packets_per_burst_received);
        packetsPerBurstTransmittedView = (TextView)myView.findViewById(R.id.statistics_packets_per_burst_transmitted);
        timeLapseBetweenBurstsReceivedView = (TextView)myView.findViewById(R.id.statistics_pause_between_bursts_received);
        timeLapseBetweenBurstsTransmittedView = (TextView)myView.findViewById(R.id.statistics_pause_between_bursts_transmitted);
        timeSpanPerBurstReceivedView = (TextView)myView.findViewById(R.id.statistics_time_of_burst_received);
        timeSpanPerBurstTransmittedView = (TextView)myView.findViewById(R.id.statistics_time_of_burst_transmitted);
        mtuView = (TextView)myView.findViewById(R.id.statistics_mtu);
        brokerIPv4View = (TextView)myView.findViewById(R.id.statistics_brokeripv4);
        brokerIPv6View = (TextView)myView.findViewById(R.id.statistics_brokeripv6);
        myIPv4View = (TextView)myView.findViewById(R.id.statistics_myipv4);
        myIPv6View = (TextView)myView.findViewById(R.id.statistics_myipv6);
        nativeRoutesView = (TextView)myView.findViewById(R.id.statistics_native_routes);
        vpnRoutesView = (TextView)myView.findViewById(R.id.statistics_vpn_routes);
        nativeDnsView = (TextView)myView.findViewById(R.id.statistics_native_dns);
        vpnDnsView = (TextView)myView.findViewById(R.id.statistics_vpn_dns);
        isRoutedView = (TextView)myView.findViewById(R.id.statistics_isrouted);
        timestampView = (TextView)myView.findViewById(R.id.statistics_timestamp);
        Log.i(TAG, "Successfully created view");
        return myView;
    }

    @Override
    public void onStart() {
        Log.d(TAG, "onStart started");

        super.onStart();
        // create the UI Handler
        Handler handler = new RedrawHandler();

        // cancel an existing updater, should not happen
        if (updaterFuture != null) {
            Log.e(StatisticsFragment.TAG, "updaterFuture existing in onStart method, should never happen. Trying to cancel");
            if (updaterFuture.cancel(true))
                Log.i(StatisticsFragment.TAG, "succeeded to cancel previous updater");
        }
        updaterFuture = null;
        // schedule an updater for execution, keeping the "Future" object returned (needed for cancellation)
        try {
            updaterFuture = executor.scheduleWithFixedDelay(new Updater(handler), 0, 1l, TimeUnit.SECONDS);
        } catch (Exception e) {
            Log.e(TAG, "Could not schedule statistics updates");
            View myView = getView();
            if (myView != null)
                myView.setVisibility(View.INVISIBLE);
        }
        Log.i(TAG, "Successfully started");
    }

    @Override
    public void onStop() {
        Log.d(TAG, "onStop");
        if (updaterFuture != null) {
            if (!updaterFuture.cancel(true))
                Log.e(StatisticsFragment.TAG, "Failed to cancel updater job");
        }
        super.onStop();
        Log.i(TAG, "Gracefully stopped");
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Log.d(TAG, "Bound to statistics service of AyiyaVpnService");
        statisticsBinder = (AyiyaVpnService.StatisticsBinder)service;
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Log.i(TAG, "Connection to statistics service of AyiyaVpnService lost");
        statisticsBinder = null;
    }

    /**
     * a private class that is scheduled for regular execution
     */
    private class Updater implements Runnable {
        private Handler handler;
        private Statistics oldstats = null;

        public Updater (Handler handler) {
            this.handler = handler;
        }

        @Override
        public void run() {
            try {
                Log.d(TAG, "Statistics refresh");
                Statistics stats = null;
                if (statisticsBinder != null) {
                    stats = statisticsBinder.getStatistics();
                } else {
                    bindToStatistics();
                }
                // if the statistics differ, send a redraw message
                if (stats != oldstats // this checks if not both are null
                   ) {
                    oldstats = stats;
                    Message redrawMessage = handler.obtainMessage(0, stats);
                    redrawMessage.sendToTarget();
                }
            } catch (Exception e) {
                Log.e(TAG, "Updating statistics failed", e);
            }
        }
    }

    /**
     * A private class that updates the UI with new Statistics.
     */
    private class RedrawHandler extends Handler {
        private NumberFormat numberFormat = NumberFormat.getNumberInstance();
        @Override
        public void handleMessage(Message inputMessage) {
            View myView = getView();
            if (myView == null)
                return; // happens during reconstruction of view hierarchy, e.g. when device orientation changed
            Log.d(TAG, "Redrawing Statistics");

            Statistics stats = (Statistics)inputMessage.obj;
            if (stats == null) {
                myView.setVisibility(View.INVISIBLE);
            } else {
                myView.setVisibility(View.VISIBLE);
                updateTextView(bytesTransmittedView, numberFormat.format(stats.getBytesTransmitted()));
                updateTextView(bytesReceivedView, numberFormat.format(stats.getBytesReceived()));
                updateTextView(packetsTransmittedView, numberFormat.format(stats.getPacketsTransmitted()));
                updateTextView(packetsReceivedView, numberFormat.format(stats.getPacketsReceived()));
                updateTextView(bytesPerBurstTransmittedView, numberFormat.format(stats.getBytesPerBurstTransmitted()));
                updateTextView(bytesPerBurstReceivedView, numberFormat.format(stats.getBytesPerBurstReceived()));
                updateTextView(packetsPerBurstTransmittedView, numberFormat.format(stats.getPacketsPerBurstTransmitted()));
                updateTextView(packetsPerBurstReceivedView, numberFormat.format(stats.getPacketsPerBurstReceived()));
                updateTextView(timeSpanPerBurstTransmittedView, numberFormat.format(stats.getTimeSpanPerBurstTransmitted()));
                updateTextView(timeSpanPerBurstReceivedView, numberFormat.format(stats.getTimeSpanPerBurstReceived()));
                updateTextView(timeLapseBetweenBurstsTransmittedView, numberFormat.format(stats.getTimeLapseBetweenBurstsTransmitted()));
                updateTextView(timeLapseBetweenBurstsReceivedView, numberFormat.format(stats.getTimeLapseBetweenBurstsReceived()));
                updateTextView(mtuView, stats.getMtu());
                updateTextView(brokerIPv4View, stats.getBrokerIPv4());
                updateTextView(brokerIPv6View, stats.getBrokerIPv6());
                updateTextView(myIPv4View, stats.getMyIPv4());
                updateTextView(myIPv6View, stats.getMyIPv6());
                updateTextView(nativeRoutesView, stats.getNativeRouting());
                updateTextView(vpnRoutesView, stats.getVpnRouting());
                updateTextView(nativeDnsView, stats.getNativeDnsSetting());
                updateTextView(vpnDnsView, stats.getVpnDnsSetting());
                updateTextView(timestampView, timestampFormatter.format(stats.getTimestamp()));
                updateTextView(isRoutedView, getString(
                        stats.isTunnelRouted() ?
                                R.string.statistics_isrouted_yes :
                                R.string.statistics_isrouted_no));
            }
        }

        /**
         * Helper method to update a TextView's text only if the text changed. Reason is that text
         * selection by the user is cancelled by TextView.setText.
         * @param textView the TextView to update
         * @param newValue the String that should be set, but could be the same as the current text
         * @return boolean, true if the text was different from the existing and the View was updated.
         */
        private boolean updateTextView (TextView textView, Object newValue) {
            String newString = (newValue == null) ? "" : String.valueOf(newValue);
            if (!newString.equals(textView.getText().toString())) {
                textView.setTextKeepState(newString);
                return true;
            }
            return false;
        }
    }
}
