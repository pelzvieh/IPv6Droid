/*
 *
 *  * Copyright (c) 2020 Dr. Andreas Feldner.
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

import java.net.InetAddress;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.android.IPv6DroidVpnService;
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


    private IPv6DroidVpnService.StatisticsBinder statisticsBinder;


    private TextView startedAt;
    private TextView reconnects;
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
        // bind to IPv6DroidVpnService for statistics
        bindToStatistics();
        timestampFormatter = android.text.format.DateFormat.getTimeFormat(getActivity());
        Log.i(TAG, "Creation successful");
    }

    /**
     * request to bind to the statistics interface of the IPv6DroidVpnService.
     */
    private void bindToStatistics() {
        Intent intent = new Intent(getActivity(), IPv6DroidVpnService.class);
        intent.setAction(IPv6DroidVpnService.STATISTICS_INTERFACE);
        if (!getActivity().bindService(intent, this, 0))
            Log.e(StatisticsFragment.TAG, "Bind request to statistics interface failed");
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy started");
        getActivity().unbindService(this);

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

        startedAt = myView.findViewById(R.id.statistics_start_datetime);
        reconnects = myView.findViewById(R.id.statistics_reconnects);
        bytesReceivedView = myView.findViewById(R.id.statistics_bytes_received);
        bytesTransmittedView = myView.findViewById(R.id.statistics_bytes_transmitted);
        packetsReceivedView = myView.findViewById(R.id.statistics_packets_received);
        packetsTransmittedView = myView.findViewById(R.id.statistics_packets_transmitted);
        bytesPerBurstReceivedView = myView.findViewById(R.id.statistics_bytes_per_burst_received);
        bytesPerBurstTransmittedView = myView.findViewById(R.id.statistics_bytes_per_burst_transmitted);
        packetsPerBurstReceivedView = myView.findViewById(R.id.statistics_packets_per_burst_received);
        packetsPerBurstTransmittedView = myView.findViewById(R.id.statistics_packets_per_burst_transmitted);
        timeLapseBetweenBurstsReceivedView = myView.findViewById(R.id.statistics_pause_between_bursts_received);
        timeLapseBetweenBurstsTransmittedView = myView.findViewById(R.id.statistics_pause_between_bursts_transmitted);
        timeSpanPerBurstReceivedView = myView.findViewById(R.id.statistics_time_of_burst_received);
        timeSpanPerBurstTransmittedView = myView.findViewById(R.id.statistics_time_of_burst_transmitted);
        mtuView = myView.findViewById(R.id.statistics_mtu);
        brokerIPv4View = myView.findViewById(R.id.statistics_brokeripv4);
        brokerIPv6View = myView.findViewById(R.id.statistics_brokeripv6);
        myIPv4View = myView.findViewById(R.id.statistics_myipv4);
        myIPv6View = myView.findViewById(R.id.statistics_myipv6);
        nativeRoutesView = myView.findViewById(R.id.statistics_native_routes);
        vpnRoutesView = myView.findViewById(R.id.statistics_vpn_routes);
        nativeDnsView = myView.findViewById(R.id.statistics_native_dns);
        vpnDnsView = myView.findViewById(R.id.statistics_vpn_dns);
        isRoutedView = myView.findViewById(R.id.statistics_isrouted);
        timestampView = myView.findViewById(R.id.statistics_timestamp);
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
        Log.d(TAG, "Bound to statistics service of IPv6DroidVpnService");
        statisticsBinder = (IPv6DroidVpnService.StatisticsBinder)service;
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Log.i(TAG, "Connection to statistics service of IPv6DroidVpnService lost");
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
                updateTextView(startedAt, timestampFormatter.format(stats.getStartedAt()));
                updateTextView(reconnects, stats.getReconnectCount());
                updateTextView(bytesTransmittedView, stats.getBytesTransmitted());
                updateTextView(bytesReceivedView, stats.getBytesReceived());
                updateTextView(packetsTransmittedView, stats.getPacketsTransmitted());
                updateTextView(packetsReceivedView, stats.getPacketsReceived());
                updateTextView(bytesPerBurstTransmittedView, stats.getBytesPerBurstTransmitted());
                updateTextView(bytesPerBurstReceivedView, stats.getBytesPerBurstReceived());
                updateTextView(packetsPerBurstTransmittedView, stats.getPacketsPerBurstTransmitted());
                updateTextView(packetsPerBurstReceivedView, stats.getPacketsPerBurstReceived());
                updateTextView(timeSpanPerBurstTransmittedView, stats.getTimeSpanPerBurstTransmitted());
                updateTextView(timeSpanPerBurstReceivedView, stats.getTimeSpanPerBurstReceived());
                updateTextView(timeLapseBetweenBurstsTransmittedView, stats.getTimeLapseBetweenBurstsTransmitted());
                updateTextView(timeLapseBetweenBurstsReceivedView, stats.getTimeLapseBetweenBurstsReceived());
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

        private boolean updateTextView (TextView textView, InetAddress newValue) {
            if (newValue == null) {
                return updateTextView(textView, "-");
            } else {
                return updateTextView(textView, newValue.getHostAddress());
            }
        }

        private boolean updateTextView (TextView textView, long newValue) {
            return updateTextView(textView, numberFormat.format(newValue));
        }

        private boolean updateTextView (TextView textView, double newValue) {
            return updateTextView(textView, numberFormat.format(newValue));
        }
    }
}
