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

package de.flyingsnail.ipv6droid.android.signinginterface;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import de.flyingsnail.ipv6droid.databinding.ProviderlistTemplateBinding;

/**
 * An ArrayAdapter that creates a specialized view for each entry of a list
 * of package names, showing information about the corresponding app.
 */
public class CertSupplierArrayAdapter extends ArrayAdapter<String> {

    private final static String TAG = CertSupplierArrayAdapter.class.getSimpleName();
    private final int viewResource;

    /**
     * Constructor. @see ArrayAdapter#ArrayAdapter.
     * @param context the Android context that this adapter's view lives in
     * @param viewResource the id of a layout resource giving each line's layout
     * @param objects a List&lt;String&gt; containing ordered package names
     */
    public CertSupplierArrayAdapter(@NonNull Context context, int viewResource, @NonNull List<String> objects) {
        super(context, viewResource, objects);
        this.viewResource = viewResource;

    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        // collect the information we need to display
        String packageName = getItem(position);
        PackageManager pm = getContext().getPackageManager();
        ApplicationInfo appInfo = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                appInfo = pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA));
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Could not query info on supplier package", e);
            }
        }
        Drawable icon = null;
        try {
            icon = pm.getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Could not query icon on supplier package", e);
        }
        // ensure we have a view
        ProviderlistTemplateBinding binding;
        if (convertView == null) {
            binding = ProviderlistTemplateBinding
                    .inflate(getContext()
                            .getSystemService(LayoutInflater.class));
            convertView = binding.getRoot();
        } else {
            binding = ProviderlistTemplateBinding.bind(convertView);
        }
        // now fill the view with data
        binding.packageName.setText(packageName);

        if (icon == null) {
            binding.appIcon.setVisibility(INVISIBLE);
        } else {
            binding.appIcon.setVisibility(VISIBLE);
            binding.appIcon.setImageDrawable(icon);
        }
        if (appInfo == null) {
            binding.description.setText("???");
        } else {
            binding.description.setText(appInfo.name);
        }
        return convertView;
    }
}
