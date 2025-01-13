/*
 *
 *  * Copyright (c) 2025 Dr. Andreas Feldner.
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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import de.flyingsnail.ipv6droid.simplecert4ipv6droid.databinding.FragmentCsrViewBinding;

public class CSRViewFragment extends Fragment {

    private static final String TAG = CSRViewFragment.class.getSimpleName();
    private FragmentCsrViewBinding binding;

    private CertSetup certSetup;
    private Handler handler;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        Log.i(TAG, "CreateView of CSRViewFragment");
        handler = new Handler(Looper.getMainLooper());
        binding = FragmentCsrViewBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.buttonNext.setOnClickListener(v ->
                NavHostFragment.findNavController(CSRViewFragment.this)
                        .navigate(R.id.action_FirstFragment_to_SecondFragment)
        );
        certSetup = (CertSetup)getActivity();
        binding.showCsr.setEnabled(false);
        binding.buttonNext.setEnabled(false);
        pollCsr();
    }

    /**
     * Repeats itself as long as no CSR has arrived from our cert service
     */
    private void pollCsr() {
        Log.i(TAG, "Polling for a CSR");
        String csr = certSetup.getCsr();
        binding.showCsr.setText(csr != null ? csr : "");
        if (csr != null) {
            Log.i(TAG, "CSR is set");
            binding.showCsr.setEnabled(true);
            binding.buttonNext.setEnabled(true);
            binding.showCsr.setOnClickListener(this::copyCsrToClipboard);
            binding.description.setText(R.string.usage_after_csr_input);
        } else {
            binding.description.setText(R.string.usage_of_csr_input);
            binding.showCsr.setEnabled(false);
            binding.buttonNext.setEnabled(false);
            handler.postDelayed(this::pollCsr, 500L);
        }
    }

    public void copyCsrToClipboard(@NonNull View view) {
        Log.i(TAG, "Copy current CSR to clipboard");
        final CharSequence csr = binding.showCsr.getText();
        final ClipboardManager clipboardManager = (ClipboardManager) (requireActivity().getSystemService(Context.CLIPBOARD_SERVICE));
        final ClipData csrClip = ClipData.newPlainText("CSR", csr);
        clipboardManager.setPrimaryClip(csrClip);
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2)
            Toast.makeText(getActivity(), "Copied", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        Log.i(TAG, "CSRViewFragment view destroyed");
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
        binding = null;
    }
}