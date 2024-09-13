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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.LinkedList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class SimpleCertificationServiceTest {

    private SimpleCertificationService certificationService;
    @Before
    public void setUp() throws Exception {
        certificationService = new SimpleCertificationService();
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void getCsr() {
        String csr = certificationService.getCsr();
        assertNull(csr);
    }

    @Test
    public void setCertChain() {
        List<String> certChain = new LinkedList<String>();
        certChain.add("Blubb");
        certChain.add("Gwonz");
        certificationService.setCertChain(certChain);
    }

    @Test
    public void onBind() {
        Context targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = new Intent(targetContext, SimpleCertificationService.class);
        targetContext.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        assertNotNull(boundService);
    }

    private SimpleCertificationService boundService = null;

    private final ServiceConnection connection = new ServiceConnection() {
        public SimpleCertificationService getCertificationService() {
            return certificationService;
        }
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance.
            SimpleCertificationService.LocalBinder binder = (SimpleCertificationService.LocalBinder) service;
            boundService = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            boundService = null;
        }
    };

}