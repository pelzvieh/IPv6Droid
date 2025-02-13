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

package de.flyingsnail.ipv6droid.android.datalayer.network;

import static org.junit.Assert.assertNotNull;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

import android.net.ConnectivityManager;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.logging.Level;
import java.util.logging.Logger;

import de.flyingsnail.ipv6droid.android.AndroidLoggingHandler;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableSource;
import io.reactivex.rxjava3.observers.TestObserver;

@RunWith(AndroidJUnit4.class)
public class NetworksRepositoryDeviceTest {
    private static final Logger logger = AndroidLoggingHandler.getLogger(NetworksRepositoryDeviceTest.class);

    private NetworksRepository repository;

    @Before
    public void setUp() {
        Logger parent = logger;
        while (parent != null) {
            parent.setLevel(Level.FINEST);
            parent = parent.getParent();
        }
        logger.info("Setting up");
        ConnectivityLocalDataSource connectivityLocalDataSource = new ConnectivityLocalDataSource(
                ApplicationProvider.getApplicationContext().getSystemService(ConnectivityManager.class));
        repository = new NetworksRepository(connectivityLocalDataSource);
    }

    @Test
    public void testRepositoryInitialization() {
        assertNotNull(repository);
    }

    @Test
    public void testConnectionOnlineFound() {
        logger.info("Starting testConnectionOnlineFound");
        Observable<Boolean> resultObservable = repository.getDeviceOnline();
        @NonNull TestObserver<Boolean> testObserver = resultObservable.test();
        Looper mainLooper = Looper.getMainLooper();
        do {
            logger.info("Main loop is not idle");

            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                logger.warning("Interrupted sleep");
            }
        } while  (!mainLooper.getQueue().isIdle());
        logger.info("Main loop is now idle");
        testObserver.assertNoErrors();
        testObserver.assertValues(FALSE,TRUE);
    }

    @Test
    public void testCurrentNetworkFound() {
        ObservableSource<NetworkProperty> resultObservableSource = repository.getCurrentNetworkObservable();
        @NonNull TestObserver<NetworkProperty> testObserver = TestObserver.create();
        resultObservableSource.subscribe(testObserver);
        Looper mainLooper = Looper.getMainLooper();
        do {
            logger.info("Main loop is not idle");

            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                logger.warning("Interrupted sleep");
            }
        } while  (!mainLooper.getQueue().isIdle());
        logger.info("Main loop is now idle");
        testObserver.assertNoErrors();
        testObserver.assertValueCount(2);
    }

}
