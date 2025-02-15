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

package de.flyingsnail.ipv6droid.android.dtlsrequest;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.flyingsnail.ipv6droid.R;
import de.flyingsnail.ipv6droid.android.AndroidLoggingHandler;

/**
 * A simple {@link Fragment} subclass to show a certificate signing request.
 */
public class KeyRequestFragment extends Fragment {
    private static final Logger logger = AndroidLoggingHandler.getLogger(KeyRequestFragment.class);
    private EditText createKeyAlias;
    private TextView csrText;
    private ArrayAdapter<String> spinnerAdapter;
    private Button copyButton;

    public KeyRequestFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        final View myView = inflater.inflate(R.layout.fragment_key_request, container, false);

        // read references to views of relevance
        createKeyAlias = myView.findViewById(R.id.keyAlias);
        csrText = myView.findViewById(R.id.csrText);
        Spinner existingKeysSpinner = myView.findViewById(R.id.selectedKeyAlias);
        Button createButton = myView.findViewById(R.id.keyCreate);
        copyButton = myView.findViewById(R.id.copyToClipboard);

        // initialise with adapters and callbacks
        List<String> aliases = new ArrayList<>(0);
        try {
            aliases = AndroidBackedKeyPair.listAliases();
        } catch (IOException e) {
            logger.log(Level.WARNING, "Cannot evaluate keys", e);
        }
        String newAlias = "IPv6Droid-" + aliases.size();
        createKeyAlias.setText(newAlias);

        spinnerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, aliases);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        existingKeysSpinner.setAdapter(spinnerAdapter);
        existingKeysSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                onKeySelected(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                onNoKeySelected();
            }
        });
        if (aliases.isEmpty())
            onNoKeySelected();
        else {
            existingKeysSpinner.setSelection(0);
        }

        createButton.setOnClickListener(v -> createNewKey(createKeyAlias.getText().toString().trim()));

        copyButton.setOnClickListener(v -> copyCsrToClipboard());

        return myView;
    }

    private void copyCsrToClipboard() {
        String csr = csrText.getText().toString();
        if (!csr.isEmpty()) {
            Context context = getContext();
            if (context != null) {
                ClipboardManager clipboardManager =
                        (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                clipboardManager.setPrimaryClip(ClipData.newPlainText("IPv6Droid CSR", csr));
            }
        }
    }

    private void onNoKeySelected() {
        csrText.setText("");
        copyButton.setEnabled(false);
    }

    private void onKeySelected(int position) {
        final String alias = spinnerAdapter.getItem(position);
        final AndroidBackedKeyPair keyPair;
        final String csrString;
        try {
            keyPair = new AndroidBackedKeyPair(alias);
            csrString = keyPair.getCertificationRequest();
        } catch (IOException e) {
            Toast.makeText(
                    getContext(),
                    "Cannot read selected key: " + e.getMessage(),
                    Toast.LENGTH_LONG)
                 .show();
            return;
        }
        csrText.setText(csrString);
        csrText.setHorizontallyScrolling(true);

        copyButton.setEnabled(true);
    }

    private void createNewKey(final String newAlias) {
        try {
            final List<String> aliases = AndroidBackedKeyPair.listAliases();
            if (aliases.contains(newAlias)) {
                throw new IllegalArgumentException("Alias already existing");
            }
            AndroidBackedKeyPair.create(newAlias);
            spinnerAdapter.insert(newAlias, aliases.size());
            spinnerAdapter.notifyDataSetChanged();
        } catch (Exception e) {
            Toast.makeText(getContext(), e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            logger.log(Level.WARNING, "failed to create new key", e);
        }
    }

}
