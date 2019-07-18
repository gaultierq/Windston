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
import com.google.maps.android.SphericalUtil;

import java.text.DateFormat;
import java.util.Date;

class Utils {


    public static final int MIN_IN_S = 60;
    public static final int S_IN_MS = 1000;
    public static final int ONE_MINUTE = MIN_IN_S * S_IN_MS;
    public static final int ONE_NM_IN_M = 1852;

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

    public static double distanceBetween(LatLng start, LatLng end) {
        return SphericalUtil.computeDistanceBetween(start, end);
    }

}