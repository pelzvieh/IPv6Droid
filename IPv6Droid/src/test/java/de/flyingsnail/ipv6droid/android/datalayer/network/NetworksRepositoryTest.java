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
import static org.mockito.Mockito.when;

import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Objects;

import de.flyingsnail.ipv6droid.android.datalayer.network.event.Event;
import de.flyingsnail.ipv6droid.android.datalayer.network.event.EventAvailable;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;

@RunWith(MockitoJUnitRunner.class)
public class NetworksRepositoryTest {

    private NetworksRepository repository;

    @Mock
    private Network testNetwork1;

    @Mock
    private ConnectivityLocalDataSource mockLocalDataSource;

    @Mock
    private NetworkLocalDataSource mockNetworkLocalDataSource;

    @Before
    public void setUp() {
        Event expectedEvent = new EventAvailable(testNetwork1);
        Observable<Event> connectivityEvents = Observable.just(expectedEvent);

        when(testNetwork1.getNetworkHandle()).thenReturn(1L);
        when(mockLocalDataSource.getConnectivityEventObservable()).thenReturn(connectivityEvents);

        repository = new NetworksRepository(mockNetworkLocalDataSource, mockLocalDataSource);
    }

    @Test
    public void testRepositoryInitialization() {
        assertNotNull(repository);
    }

    @Test
    public void getConnectivityEvents_returnsEventsFromLocalDataSource() {
        // Given
        LinkProperties linkProperties = new LinkProperties();
        NetworkCapabilities networkCapabilities = new NetworkCapabilities();

        // When
        Observable<NetworksRepository.NetworksInformationContainer> resultObservable = repository.getNetworksProperties();

        // Then
        @NonNull TestObserver<NetworksRepository.NetworksInformationContainer> testObserver = resultObservable.test();
        testObserver.assertNoErrors();
        testObserver.assertValue((nic) -> nic.getId() == 1L);
        testObserver.assertValue((nic) -> nic.getNetworkProperties() != null);
        testObserver.assertValue((nic) -> Objects.requireNonNull(nic.getNetworkProperties().get(1L)).getProperties() == null);
        testObserver.assertValue((nic) -> Objects.requireNonNull(nic.getNetworkProperties().get(1L)).getCapabilities() == null);
    }

    @Test
    public void getConnectivityEvents_returnsOnlineNetwork() {
        Observable<Long> resultObservable = repository.getOnlineNetwork();
        @NonNull TestObserver<Long> testObserver = resultObservable.test();
        testObserver.assertNoErrors();
        testObserver.assertNoValues();
    }

    @Test
    public void getConnectivityEvents_returnsDeviceOnline() {
        Observable<Boolean> resultObservable = repository.getDeviceOnline();
        @NonNull TestObserver<Boolean> testObserver = resultObservable.test();
        testObserver.assertNoErrors();
        testObserver.assertValue(Boolean.FALSE);
    }

    @Test
    public void getConnectivityEvents_returnsCurrentNetwork() {
        Observable<Network> resultObservable = repository.getCurrentNetworkObservable();
        @NonNull TestObserver<Network> testObserver = resultObservable.test();
        testObserver.assertNoErrors();
        testObserver.assertValue((n)->n.getNetworkHandle()==1L);
    }
}
