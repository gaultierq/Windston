/**
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.coderouge.windston;


import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.preference.PreferenceManager;
import com.google.android.gms.maps.model.LatLng;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

class Utils {


    public static final int MIN_IN_S = 60;
    public static final int S_IN_MS = 1000;
    public static final int ONE_MINUTE = MIN_IN_S * S_IN_MS;
    public static final int ONE_NM_IN_M = 1852;
    public static final String KEY_LAST_SENT_DATE = "KEY_LAST_SENT_DATE";
    public static final String KEY_TARGET_BEARING = "KEY_TARGET_BEARING";
    public static final String KEY_OPTIONS_OPENED = "KEY_OPTIONS_OPENED";
    public static final Date START_DATE = startDate();


    private static Date startDate() {
        String input = "01/06/2019";

        SimpleDateFormat parser = new SimpleDateFormat("dd/M/yyyy");
        try {
            return parser.parse(input);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    static int getUpdateIntervalMs(Context context) {
        String s = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.pref_key_update_interval_ms), "" + ONE_MINUTE);
        return Integer.parseInt(s);
    }

    static int getSmallestDisplacementM(Context context) {
        String s = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.pref_key_smallest_displacement_m), "500");
        return Integer.parseInt(s);
    }

    /**
     * Returns the {@code location} object as a human readable string.
     * @param location  The {@link Location}.
     */
    static String getLocationText(Location location) {
        return location == null ? "Unknown location" :
                "(" + location.getLatitude() + ", " + location.getLongitude() + ")";
    }

    static String getLocationTitle(Context context) {
        return "Tracking active";
//        return context.getString(R.string.location_updated,
//                DateFormat.getDateTimeInstance().format(new Date()));
    }

    static Date readLastSentDate(Context context) {
        long dateMs = PreferenceManager.getDefaultSharedPreferences(context)
                .getLong(KEY_LAST_SENT_DATE, 0);

        if (dateMs == 0) return START_DATE;
        return new Date(dateMs);
    }

    static void writeLastSentDate(Context context, Date lastSent) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putLong(KEY_LAST_SENT_DATE, lastSent.getTime())
                .apply();
    }

    static Long readTargetBearing(Context context) {
        long aLong = PreferenceManager.getDefaultSharedPreferences(context)
                .getLong(KEY_TARGET_BEARING, -1);
        if (aLong == -1) return null;
        return aLong;
    }

    static void writeTargetBearing(Context context, Long targetBearing) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putLong(KEY_TARGET_BEARING, targetBearing == null ? -1 : targetBearing)
                .apply();
    }

    static boolean readOptionsOpened(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(KEY_OPTIONS_OPENED, false);
    }

    static void writeOptionsOpened(Context context, boolean opened) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(KEY_OPTIONS_OPENED, opened)
                .apply();
    }

    public static void autoLaunchVivo(Context context) {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"));
            context.startActivity(intent);
        } catch (Exception e) {
            try {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName("com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"));
                context.startActivity(intent);
            } catch (Exception ex) {
                try {
                    Intent intent = new Intent();
                    intent.setClassName("com.iqoo.secure",
                            "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager");
                    context.startActivity(intent);
                } catch (Exception exx) {
                    ex.printStackTrace();
                }
            }
        }
    }

    public static double bearing(LatLng s, LatLng e){
        return bearing(s.latitude, s.longitude, e.latitude, e.longitude);
    }


    public static double bearing(double startLat, double startLng, double endLat, double endLng){
        double latitude1 = Math.toRadians(startLat);
        double latitude2 = Math.toRadians(endLat);
        double longDiff= Math.toRadians(endLng - startLng);
        double y= Math.sin(longDiff)*Math.cos(latitude2);
        double x=Math.cos(latitude1)*Math.sin(latitude2)-Math.sin(latitude1)*Math.cos(latitude2)*Math.cos(longDiff);

        return (Math.toDegrees(Math.atan2(y, x))+360)%360;
    }


}