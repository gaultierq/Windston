package com.coderouge.windston

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity
data class Waypoint(
    @ColumnInfo(name = "lat") val lat: Double,
    @ColumnInfo(name = "lng") val lng: Double,
    @ColumnInfo(name = "date") val date: Date
) {
    @PrimaryKey(autoGenerate = true) var localuid: Int = 0;
    @ColumnInfo(name = "uid") var uid: Int? = null;
}