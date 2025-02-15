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

import java.util.logging.Logger;

/**
 * Mock version for unit tests: does in fact nothing but implement the getLogger
 * Method to return an ordinary java.util.logging.Logger.
 */
public class AndroidLoggingHandler {
    /**
     * Constructs a new instance of the Android log handler.
     */
    private AndroidLoggingHandler() {
    }

    /**
     * Construct a canonical name Logger for the given class with
     * the logging level pre-set to what the Android Log is suggesting.
     * This log level can programmatically changed by @ref{java.util.Logger#setLevel}
     * and will then generate different granularity of log data.
     * @param ofClass the Class that is going to use the logger
     * @return a Logger for the given class with the suitable log level set.
     */
    public static Logger getLogger(Class<?> ofClass) {
        final String loggerName = ofClass.getName();
        return Logger.getLogger(loggerName);
    }
}