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

package de.flyingsnail.ipv6droid.android.vpnrun;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import de.flyingsnail.ipv6droid.android.UserNotificationCallback;
import de.flyingsnail.ipv6droid.transport.TransporterBuilder;
import de.flyingsnail.ipv6droid.transport.TunnelSpec;

@RunWith(AndroidJUnit4.class)
public class RemoteEndTest implements UserNotificationCallback {

    private RemoteEnd remoteEnd;
    private Throwable notifiedError;
    private List<Integer> toasted;

    @Before
    public void setUp() throws Exception {
        toasted = new ArrayList<>();
        VpnStatusReport report = new VpnStatusReport(ApplicationProvider.getApplicationContext());
        TransporterBuilder.register(TunnelSpecMock.class, TransporterMock.class);
        TunnelSpec tunnel = new TunnelSpecMock() ;
        remoteEnd = new RemoteEnd(report, true, true, this, tunnel);
    }

    @After
    public void tearDown() {
        if (remoteEnd != null)
            remoteEnd.stop();
    }

    @Test
    public void testIsNetworkMobile() {
        boolean isMobile = remoteEnd.isNetworkMobile();
        assertFalse(isMobile);
        assertNull(notifiedError);
        assertArrayEquals(toasted.toArray(), new Integer[]{});
    }


    @Override
    public void notifyUserOfError(int resourceId, @NonNull Throwable e) {
        this.notifiedError = e;
    }

    @Override
    public void notifyUserOfErrorCancel() {
        this.notifiedError = null;
    }

    @Override
    public void postToast(int resId, int duration) {
        this.toasted.add(resId);
    }
}