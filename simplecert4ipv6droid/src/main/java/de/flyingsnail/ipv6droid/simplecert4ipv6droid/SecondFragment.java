/*
 *
 *  * Copyright (c) 2024 Dr. Andreas Feldner.
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

package de.flyingsnail.ipv6droid.simplecert4ipv6droid;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.snackbar.Snackbar;

import java.util.Arrays;
import java.util.List;

import de.flyingsnail.ipv6droid.simplecert4ipv6droid.databinding.FragmentSecondBinding;

public class SecondFragment extends Fragment {
    private static final String TAG = SecondFragment.class.getSimpleName();

    private FragmentSecondBinding binding;
    private CertSetup certSetup;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        Log.i(TAG, "View creating");
        binding = FragmentSecondBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        certSetup = (CertSetup)requireActivity();

        binding.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAnchorView(R.id.fab)
                        .setAction("Action", (v) -> {postCertChain();}).show();
            }
        });

        binding.buttonSecond.setOnClickListener(v ->
                NavHostFragment.findNavController(SecondFragment.this)
                        .navigate(R.id.action_SecondFragment_to_FirstFragment)
        );
    }

    private void postCertChain() {
        List<String> certChain = Arrays.asList(
                binding.edittextDeviceCert.getText().toString(),
                binding.edittextCaCert.getText().toString());
        if (!certSetup.setCertChain(certChain)) {
            Toast.makeText(getContext(), "Cannot write back cert chain", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Service not set, unable to write back cert chain");
        } else {
            Log.i(TAG, "Wrote back cert chain");
        }
    }

    @Override
    public void onDestroyView() {
        Log.i(TAG, "destroying view");
        super.onDestroyView();
        binding = null;
    }

}