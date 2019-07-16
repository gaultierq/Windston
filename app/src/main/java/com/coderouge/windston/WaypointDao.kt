package com.coderouge.windston

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface WaypointDao {
    @Query("SELECT * FROM waypoint")
    fun getAll(): List<Waypoint>

    @Query("SELECT * FROM waypoint WHERE uid IN (:waypointIds)")
    fun loadAllByIds(waypointIds: IntArray): List<Waypoint>

    @Insert
    fun insertAll(vararg waypoints: Waypoint)

    @Delete
    fun delete(waypoint: Waypoint)
}