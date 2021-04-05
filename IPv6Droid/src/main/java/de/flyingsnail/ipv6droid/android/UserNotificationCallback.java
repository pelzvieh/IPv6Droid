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

import androidx.annotation.NonNull;

public interface UserNotificationCallback {
    /**
     * Generate a user notification with the supplied expection's cause as detail message.
     *
     * @param resourceId the string resource supplying the notification title
     * @param e          the Exception the cause of which is to be displayed
     */
    void notifyUserOfError(int resourceId, @NonNull Throwable e);

    /**
     * Cancel an error notification, if currently active.
     */
    void notifyUserOfErrorCancel();

    /**
     * Generate an Android Toast
     * @param resId the ressource ID of the string to post
     * @param duration constant coding the duration
     */
    void postToast (final int resId, final int duration);

}
