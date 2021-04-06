/*
 *
 *  * Copyright (c) 2021 Dr. Andreas Feldner.
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

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.List;

import de.flyingsnail.ipv6droid.transport.TunnelSpec;

/**
 * Implements the TunnelPersisting interface for storing to a file.
 * Created by pelzi on 03.02.16.
 */
public class TunnelPersistingFile implements TunnelPersisting  {
    private final Context context;

    /**
     * A String giving the file name for persisting tunnel information
     */
    private static final String FILE_LAST_TUNNEL = "last_tunnel";


    /**
     * The tag to use for logging
     */
    private static final String TAG = TunnelPersistingFile.class.getName();

    /**
     * Constructor. Takes a Context object (required).
     * @param context the Context object to which file access is delegated.
     */
    public TunnelPersistingFile (@NonNull Context context) {
        this.context = context;
    }

    /**
     * Read the last persisted state of tunnels from storage.
     *
     * @return a @ref Tunnels object
     * @throws IOException in case of data access problems.
     */
    @Override
    public @NonNull Tunnels readTunnels() throws IOException {
        // open private file
        InputStream is = context.openFileInput(FILE_LAST_TUNNEL);
        ObjectInputStream os = new ObjectInputStream(is);
        List<? extends TunnelSpec> cachedTunnels;
        try {
            cachedTunnels = (List<? extends TunnelSpec>)os.readObject();
        } catch (ClassNotFoundException | InvalidClassException | RuntimeException e) {
            throw new IOException("Unable to read tunnel definitions from persistence media", e);
        }
        if (cachedTunnels instanceof Tunnels) {
            return (Tunnels) cachedTunnels;
        } else {
            // this is for reading the previous file format
            int selected = os.readInt();
            TunnelSpec tunnel = cachedTunnels.get(selected);
            return new Tunnels(cachedTunnels, tunnel);
        }
    }

    /**
     * Write the supplied Tunnels to  a private file. Format is:
     * or a Tunnels tunnels; int selected;
     *
     * @param tunnels the Tunnels object representing the list of available tunnels and the
     *                selected tunnel.
     * @throws IOException in case of problems writing data.
     */
    @Override
    public void writeTunnels(@NonNull Tunnels tunnels) throws IOException {
        OutputStream fs = context.openFileOutput(FILE_LAST_TUNNEL, Context.MODE_PRIVATE);
        ObjectOutputStream os = new ObjectOutputStream(fs);
        os.writeObject(tunnels);
        os.writeInt(tunnels.indexOf(tunnels.getActiveTunnel()));
        os.close();
        fs.close();
    }

}
