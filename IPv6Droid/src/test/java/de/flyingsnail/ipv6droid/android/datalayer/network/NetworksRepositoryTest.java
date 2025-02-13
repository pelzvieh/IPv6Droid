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

import static android.net.NetworkCapabilities.NET_CAPABILITY_FOREGROUND;
import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import de.flyingsnail.ipv6droid.android.AndroidLoggingHandler;
import de.flyingsnail.ipv6droid.android.datalayer.network.event.Event;
import de.flyingsnail.ipv6droid.android.datalayer.network.event.EventAvailable;
import de.flyingsnail.ipv6droid.android.datalayer.network.event.EventBlockingChanged;
import de.flyingsnail.ipv6droid.android.datalayer.network.event.EventCapabilitiesChanged;
import de.flyingsnail.ipv6droid.android.datalayer.network.event.EventDisconnected;
import de.flyingsnail.ipv6droid.android.datalayer.network.event.EventDisconnecting;
import de.flyingsnail.ipv6droid.android.datalayer.network.event.EventLinkPropertiesChanged;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableSource;
import io.reactivex.rxjava3.observers.TestObserver;

@RunWith(MockitoJUnitRunner.class)
public class NetworksRepositoryTest {
    private static final Logger logger = AndroidLoggingHandler.getLogger(NetworksRepositoryTest.class);

    private NetworksRepository repository;

    @Mock
    private Network testNetwork1;

    @Mock
    private Network testNetwork3;

    @Mock
    private ConnectivityLocalDataSource mockLocalDataSource;

    @Mock
    private NetworkCapabilities networkCapabilities_noInternet;

    @Mock
    private NetworkCapabilities networkCapabilities_connected;

    private final LinkProperties linkProperties1 = new LinkProperties();
    private final LinkProperties linkProperties3 = new LinkProperties();

    private Event[] emittedTestEvents;

    @Before
    public void setUp() {
        Logger parent = logger;
        while (parent != null) {
            parent.setLevel(Level.FINEST);
            parent = parent.getParent();
        }
        logger.info("Setting up");
        // Given
        /*
          Connectivity	  -acbpc--g-l-ab----acpb--l
                           11111  1 1 33    1111  3
        */
        emittedTestEvents = new Event[] {
                new EventAvailable(testNetwork1),
                new EventCapabilitiesChanged(testNetwork1, networkCapabilities_noInternet),
                new EventBlockingChanged(testNetwork1, FALSE),
                new EventLinkPropertiesChanged(testNetwork1, linkProperties1),
                new EventCapabilitiesChanged(testNetwork1, networkCapabilities_connected),
                new EventDisconnecting(testNetwork1, 100),
                new EventDisconnected(testNetwork1),
                new EventAvailable(testNetwork3),
                new EventBlockingChanged(testNetwork1, FALSE),
                new EventAvailable(testNetwork1),
                new EventCapabilitiesChanged(testNetwork1, networkCapabilities_connected),
                new EventLinkPropertiesChanged(testNetwork1, linkProperties3),
                new EventBlockingChanged(testNetwork1, FALSE),
                new EventDisconnected(testNetwork3),
        };
        Observable<Event> connectivityEvents = Observable.fromArray(emittedTestEvents);

        when(testNetwork1.getNetworkHandle()).thenReturn(1L);
        when(testNetwork3.getNetworkHandle()).thenReturn(3L);
        when(networkCapabilities_noInternet.hasCapability(NET_CAPABILITY_INTERNET))
                .thenReturn(false);
        when(networkCapabilities_connected.hasCapability(anyInt()))
                .thenReturn(true);
        when(mockLocalDataSource.getConnectivityEventObservable()).thenReturn(connectivityEvents);

        repository = new NetworksRepository(mockLocalDataSource);
    }

    @Test
    public void testRepositoryInitialization() {
        assertNotNull(repository);
    }

    @Test
    public void getConnectivityEvents_returnsNetworksProperties() {

        // When
        ObservableSource<NetworksRepository.NetworksInformationContainer> resultObservableSource = repository.getNetworksProperties();

        // Then
        @NonNull TestObserver<NetworksRepository.NetworksInformationContainer> testObserver = TestObserver.create();
        resultObservableSource.subscribe(testObserver);
        testObserver.assertNoErrors();
        testObserver.assertValueCount(emittedTestEvents.length);
        testObserver.assertValueAt(0, (nic) -> nic.getId() == 1L);
        testObserver.assertValueAt(0, (nic) -> nic.getNetworkProperties() != null);
        testObserver.assertValueAt(0, (nic) -> Objects.requireNonNull(nic.getNetworkProperties().get(1L)).getProperties() == null);
        testObserver.assertValueAt(0, (nic) -> Objects.requireNonNull(nic.getNetworkProperties().get(1L)).getCapabilities() == null);
        testObserver.assertValueAt(4, (nic) ->
                Objects.requireNonNull(
                        Objects.requireNonNull(nic.getNetworkProperties().get(1L))
                                .getCapabilities()).hasCapability(NET_CAPABILITY_INTERNET));
        testObserver.assertValueAt(4, (nic) ->
                Objects.requireNonNull(
                        Objects.requireNonNull(nic.getNetworkProperties().get(1L))
                                .getCapabilities()).hasCapability(NET_CAPABILITY_FOREGROUND));
    }

    @Test
    public void getConnectivityEvents_returnsOnlineNetwork() {
        Observable<NetworkProperty> resultObservable = repository.getOnlineNetworkProperty();
        @NonNull TestObserver<NetworkProperty> testObserver = resultObservable.test();
        testObserver.assertNoErrors();
        /*
        Connectivity	  -acbpc--g-l-ab----acpb--l
		                     11111  1 1 33    1111  3
        OnlineNet	      -----1--1------------1---
         */
        testObserver.assertValueAt(0, (np) -> np.getNetwork().getNetworkHandle() == 1L);
        testObserver.assertValueAt(1, (np) -> np.getNetwork().getNetworkHandle() == 1L);
        testObserver.assertValueAt(2, (np) -> np.getNetwork().getNetworkHandle() == 1L);
    }

    @Test
    public void getConnectivityEvents_returnsDeviceOnline() {
        Observable<Boolean> resultObservable = repository.getDeviceOnline();
        @NonNull TestObserver<Boolean> testObserver = resultObservable.test();
        testObserver.assertNoErrors();
        /*
        Connectivity	  -acbp--g-l-ab----acpb--l
		                     1111  1 1 33    1111  3
        OnlineDev	      -0--1----0----------1---

         */
        testObserver.assertValues(FALSE, TRUE, FALSE);
    }

    @Test
    public void getConnectivityEvents_returnsCurrentNetwork() {
        ObservableSource<NetworkProperty> resultObservableSource = repository.getCurrentNetworkObservable();
        @NonNull TestObserver<NetworkProperty> testObserver = TestObserver.create();
        resultObservableSource.subscribe(testObserver);
        testObserver.assertNoErrors();
        /*
        Connectivity	  -acbp--g-l-ab----acpb--l
		                     1111  1 1 33    1111  3
        CurrentNetwork?	 1         3     1
         */
        testObserver.assertValueAt(0, (np) -> np.getNetwork() == testNetwork1);
        testObserver.assertValueAt(1, (np) -> np.getNetwork() == testNetwork3);
        testObserver.assertValueAt(2, (np) -> np.getNetwork() == testNetwork1);
    }
}
