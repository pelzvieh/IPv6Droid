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

import android.util.Log;

import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Implements a {@link java.util.logging.Logger} handler that writes to the Android log. The
 * implementation is rather straightforward. The name of the logger serves as
 * the log tag. Only the log levels need to be converted appropriately. For
 * this purpose, the following mapping is being used:
 *
 * <table>
 *   <tr>
 *     <th>logger level</th>
 *     <th>Android level</th>
 *   </tr>
 *   <tr>
 *     <td>
 *       SEVERE
 *     </td>
 *     <td>
 *       ERROR
 *     </td>
 *   </tr>
 *   <tr>
 *     <td>
 *       WARNING
 *     </td>
 *     <td>
 *       WARN
 *     </td>
 *   </tr>
 *   <tr>
 *     <td>
 *       INFO
 *     </td>
 *     <td>
 *       INFO
 *     </td>
 *   </tr>
 *   <tr>
 *     <td>
 *       CONFIG
 *     </td>
 *     <td>
 *       DEBUG
 *     </td>
 *   </tr>
 *   <tr>
 *     <td>
 *       FINE, FINER, FINEST
 *     </td>
 *     <td>
 *       VERBOSE
 *     </td>
 *   </tr>
 * </table>
 */
public class AndroidLoggingHandler extends Handler {

    private final static AndroidLoggingHandler instance;

    public static AndroidLoggingHandler getInstance() {
        return instance;
    }

    /**
     * Holds the formatter for all Android log handlers.
     */
    private static final Formatter THE_FORMATTER;

    static {
        THE_FORMATTER = new Formatter() {
            @Override
            public String format(LogRecord r) {
                Throwable thrown = r.getThrown();
                if (thrown != null) {
                    return r.getMessage() + '\n' +
                            Log.getStackTraceString(thrown);
                } else {
                    return r.getMessage();
                }
            }
        };

        instance = new AndroidLoggingHandler();
    }

    /**
     * Constructs a new instance of the Android log handler.
     */
    private AndroidLoggingHandler() {
        setFormatter(THE_FORMATTER);
    }

    @Override
    public void close() {
        // No need to close, but must implement abstract method.
    }

    @Override
    public void flush() {
        // No need to flush, but must implement abstract method.
    }

    /**
     * Returns the short logger tag (up to 23 chars) for the given logger name.
     * Traditionally loggers are named by fully-qualified Java classes; this
     * method attempts to return a concise identifying part of such names.
     */
    private static String loggerNameToTag(String loggerName) {
        // Anonymous logger.
        if (loggerName == null) {
            return "null";
        }

        int length = loggerName.length();
        if (length <= 23) {
            return loggerName;
        }

        int lastPeriod = loggerName.lastIndexOf(".");
        return length - (lastPeriod + 1) <= 23
                ? loggerName.substring(lastPeriod + 1)
                : loggerName.substring(loggerName.length() - 23);
    }

    @Override
    public void publish(LogRecord record) {
        int level = getAndroidLevel(record.getLevel());
        String tag = loggerNameToTag(record.getLoggerName());

        try {
            String message = getFormatter().format(record);
            Log.println(level, tag, message);
        } catch (RuntimeException e) {
            Log.e("AndroidHandler", "Error logging message.", e);
        }
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
        Logger logger = Logger.getLogger(loggerName);
        final String tag = loggerNameToTag(loggerName);
        final Level sourceLevel =
                Log.isLoggable(tag, Log.VERBOSE) ? Level.ALL
                : Log.isLoggable(tag, Log.DEBUG) ? Level.FINER
                : Log.isLoggable(tag, Log.INFO) ? Level.INFO
                : Log.isLoggable(tag, Log.WARN) ? Level.WARNING
                : Log.isLoggable(tag, Log.ERROR) ? Level.SEVERE
                : Level.OFF;
        logger.setLevel(sourceLevel);
        logger.log(sourceLevel, String.format("Logger %s starting with minimum level %s, and tag %s",
                loggerName, sourceLevel.getName(), tag));
        return logger;
    }

    /**
     * Converts a {@link java.util.logging.Logger} logging level into an Android one.
     *
     * @param level The {@link java.util.logging.Logger} logging level.
     *
     * @return The resulting Android logging level.
     */
    static int getAndroidLevel(Level level) {
        int value = level.intValue();
        if (value >= Level.SEVERE.intValue()) { // SEVERE
            return Log.ERROR;
        } else if (value >= Level.WARNING.intValue()) { // WARNING
            return Log.WARN;
        } else if (value >= Level.INFO.intValue()) { // INFO
            return Log.INFO;
        } else {
            return Log.DEBUG;
        }
    }
}