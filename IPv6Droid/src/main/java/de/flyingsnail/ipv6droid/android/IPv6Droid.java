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

import java.util.logging.Logger;

import de.flyingsnail.ipv6droid.android.datalayer.network.ConnectivityLocalDataSource;
import de.flyingsnail.ipv6droid.android.datalayer.network.NetworkLocalDataSource;
import de.flyingsnail.ipv6droid.android.datalayer.network.NetworksRepository;

public class IPv6Droid extends Application {
    static final Logger logger = Logger.getLogger(IPv6Droid.class.getName());

    private NetworksRepository networksRepository;
    private ConnectivityLocalDataSource connectivityLocalDataSource;
    private NetworkLocalDataSource networkLocalDataSource;

    @Override
    public void onCreate() {
        super.onCreate();
        logger.fine("Application starting up");
        connectivityLocalDataSource = new ConnectivityLocalDataSource(getSystemService(ConnectivityManager.class));
        networkLocalDataSource = new NetworkLocalDataSource();
        networksRepository = new NetworksRepository(networkLocalDataSource, connectivityLocalDataSource);
    }

    public NetworksRepository getNetworksRepository() {
        return networksRepository;
    }
}