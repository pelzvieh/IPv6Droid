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

package de.flyingsnail.ipv6droid.android.googlesubscription;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import de.flyingsnail.ipv6droid.R;

/**
 * A simple {@link Fragment} subclass that only shows a message why it is not possible to see or purchase anything.
 */
public class NoTunnelFragment extends Fragment {

    public static final String ARG_MESSAGE = "message";

    private String message;

    private static final String TAG = NoTunnelFragment.class.getSimpleName();

    /** A TextView showing user-readable information about subscription status */
    private TextView messageView;

    public NoTunnelFragment() {
        super(R.layout.fragment_no_tunnel);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "Creating NoTunnelFragment");
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            message = getArguments().getString(ARG_MESSAGE);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.i(TAG, "onCreateView");
        View myView = inflater.inflate(R.layout.fragment_no_tunnel, container, false);
        messageView = myView.findViewById(R.id.no_tunnel_message);
        // Inflate the layout for this fragment
        return myView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (message != null && messageView != null) {
            messageView.setText(message);
            Log.i(TAG, "Displaying message " + message);
        } else {
            Log.i(TAG, "Not displaying message " + message);
        }
    }
}