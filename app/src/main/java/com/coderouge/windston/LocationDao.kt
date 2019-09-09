package com.coderouge.windston

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LocationDao {

    @Query("SELECT * FROM locationdata")
    fun getAll(): List<LocationData>

    @Query("SELECT * FROM locationdata ORDER BY date DESC limit 10")
    fun getTail(): List<LocationData>

    @Query("SELECT * FROM locationdata WHERE uid IN (:locationIds)")
    fun loadAllByIds(locationIds: IntArray): List<LocationData>

    @Query("SELECT * FROM locationdata WHERE lat = (:lat) AND lng = (:lng)")
    fun findByLatLng(lat: Double, lng: Double): List<LocationData>

    @Insert
    fun insertAll(vararg locationData: LocationData)

    @Delete
    fun delete(locationData: LocationData)

    @Query("DELETE FROM locationdata")
    fun purge()
}