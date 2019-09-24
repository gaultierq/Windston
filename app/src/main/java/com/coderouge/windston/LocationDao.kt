package com.coderouge.windston

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import java.util.*

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

    @Query("SELECT AVG(speed) FROM locationdata WHERE date BETWEEN (:from) AND (:to)")
    fun averageSpeed(from: Date, to: Date): Float?

    @Query("SELECT * FROM locationdata WHERE date BETWEEN (:from) AND (:to)")
    fun selectBetween(from: Date, to: Date): List<LocationData>

    @Query("SELECT COUNT(*) FROM locationdata WHERE date BETWEEN (:from) AND (:to)")
    fun countBetween(from: Date, to: Date): Int

    @Query("SELECT * FROM locationdata WHERE date < (:date) order by date desc limit 1")
    fun selectJustBefore(date: Date): LocationData?

    @Query("SELECT * FROM locationdata WHERE date > (:date) order by date asc limit 1")
    fun selectJustAfter(date: Date): LocationData?

    @Insert
    fun insertAll(vararg locationData: LocationData)

    @Delete
    fun delete(locationData: LocationData)

    @Query("DELETE FROM locationdata")
    fun purge()
}