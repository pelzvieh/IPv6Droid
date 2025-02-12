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

package de.flyingsnail.ipv6droid.android;

import android.app.Application;
import android.net.ConnectivityManager;

import androidx.annotation.NonNull;

import java.util.logging.Logger;

import de.flyingsnail.ipv6droid.android.datalayer.network.ConnectivityLocalDataSource;
import de.flyingsnail.ipv6droid.android.datalayer.network.NetworkLocalDataSource;
import de.flyingsnail.ipv6droid.android.datalayer.network.NetworksRepository;

public class IPv6Droid extends Application {
    static final Logger logger = Logger.getLogger(IPv6Droid.class.getName());
    static private IPv6Droid instance = null;

    private NetworksRepository networksRepository;

    public IPv6Droid() {
        logger.fine("Application constructor");
        if (instance != null) {
            throw new IllegalStateException("Attempt to construct Application object twice");
        }
        instance = this;
    }

    public static @NonNull IPv6Droid getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Application not yet constructed");
        }
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        logger.fine("Application starting up");
        ConnectivityLocalDataSource connectivityLocalDataSource = new ConnectivityLocalDataSource(getSystemService(ConnectivityManager.class));
        NetworkLocalDataSource networkLocalDataSource = new NetworkLocalDataSource();
        networksRepository = new NetworksRepository(networkLocalDataSource, connectivityLocalDataSource);
    }

    public NetworksRepository getNetworksRepository() {
        return networksRepository;
    }
}