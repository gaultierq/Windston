package com.coderouge.windston;

import androidx.annotation.Nullable;

import java.util.Date;

//TODO: check equals and hashcode implementations
public final class MarkerKey {

    private final Double latitude;
    private final Double longitude;
    private final Date date;

    public MarkerKey(Double latitude, Double longitude, Date date) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.date = date;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public Date getDate() {
        return date;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (!(obj instanceof MarkerKey)) return false;
        MarkerKey key = (MarkerKey) obj;
        return doubleEquals(this.latitude, key.getLatitude()) &&
                doubleEquals(this.longitude, key.getLongitude()) &&
                dateEquals(this.date, key.getDate());

    }

    @Override
    public int hashCode() {
        int r = 0;
        if (this.latitude != null) r += this.latitude.hashCode() ^ 37;
        if (this.longitude != null) r += this.longitude.hashCode() ^ 13;
        if (this.date != null) r += this.date.getTime() ^ 17;
        return r;
    }

    private static boolean doubleEquals(Double a, Double b) {
        if (a == null || b == null) return a == null && b == null;
        return a.doubleValue() == b.doubleValue();
    }

    private static boolean dateEquals(Date a, Date b) {
        if (a == null || b == null) return a == null && b == null;
        return a.getTime() == b.getTime();
    }
}
