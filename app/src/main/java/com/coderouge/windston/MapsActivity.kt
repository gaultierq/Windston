package com.coderouge.windston

import android.Manifest
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashSet
import android.view.MenuItem
import android.content.ActivityNotFoundException
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private lateinit var mMap: GoogleMap

    private var PRIVATE_MODE = 0
    private val PREF_NAME = "waypoints"
    private val PERM_REQ_CODE = 32
    private val DATE_FORMAT = SimpleDateFormat("dd/M/yyyy HH:mm:ss")
    private var mLocationManager : LocationManager? = null

    companion object {
        private fun join(set: Set<String>?, sep: String): String? {
            if (set == null) return null
            val sb = StringBuilder();
            val it = set.iterator();

            if(it.hasNext()) {
                sb.append(it.next());
            }
            while(it.hasNext()) {
                sb.append(sep).append(it.next());
            }
            return  sb.toString();
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mLocationManager = getSystemService(LOCATION_SERVICE) as LocationManager?;
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("gmt"));
        this.findViewById<FloatingActionButton>(R.id.fab).setOnClickListener { v ->

            onFloatClick()
        }
    }

    @SuppressLint("MissingPermission")
    private fun onFloatClick() {
        run {


            if (canAccessLocation()) {
                val location = mLocationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER);

                val lat = location?.latitude
                val lng = location?.longitude

                addWaypoint(lat, lng, DATE_FORMAT.format(Date()))

                if (lat != null && lng != null) {
                    showWaypoint(lat, lng)
                }


                showToast("waypoint added")
            }
            else {
                showToast("cannot access location")
            }

        }
    }

    private fun showToast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.getItemId()) {
            R.id.action_send -> sendEmail()
        }
        return true
    }

    private fun sendEmail() {
        val bodyText = bodyText()
        if (bodyText.isNullOrEmpty()) {
            showToast("nothing to send")
            return
        }
        val mailto = "mailto:geopos@coderouge.ovh" +
                "?subject=" + Uri.encode("Sent from Windston") +
                "&body=" + Uri.encode(bodyText)

        val emailIntent = Intent(Intent.ACTION_SENDTO)
        emailIntent.data = Uri.parse(mailto)

        try {
            startActivity(emailIntent)
            purge()
        } catch (e: ActivityNotFoundException) {
            //TODO: Handle case where no email app is available
            showToast("cannot send email")
        }
    }

    private fun bodyText(): String? {
        val sharedPref: SharedPreferences = getSharedPreferences(PREF_NAME, PRIVATE_MODE)
        val waypoints = sharedPref.getStringSet(PREF_NAME, HashSet())
        return join(waypoints, "\n===\n")
    }

    private fun addWaypoint(lat: Double?, lng: Double?, date: String?) {
        if (lat == null || lng == null) {
            showToast("location not available")
            return
        }
        val sharedPref: SharedPreferences = getSharedPreferences(PREF_NAME, PRIVATE_MODE)
        val editor = sharedPref.edit()
        val set = HashSet(sharedPref.getStringSet(PREF_NAME, HashSet<String>()))
        set.add("latlng: $lat, $lng\ndate: $date")
        editor.putStringSet(PREF_NAME, set)
        editor.apply()
    }

    private fun purge() {
        val sharedPref: SharedPreferences = getSharedPreferences(PREF_NAME, PRIVATE_MODE)
        val editor = sharedPref.edit()
        editor.putStringSet(PREF_NAME, HashSet<String>())
        editor.apply()
    }

    private fun getWaypoints(): List<List<String>> {
        val sharedPref: SharedPreferences = getSharedPreferences(PREF_NAME, PRIVATE_MODE)
        val waypoints = sharedPref.getStringSet(PREF_NAME, HashSet())
        return waypoints.orEmpty().map { wps -> extLatLng(wps) }
    }

    private fun extLatLng(wps: String): List<String> {
        return wps
            .split('\n')
            .get(0)
            .substringAfter("latlng: ")
            .split(", ")
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap


        if (!canAccessLocation()) {
            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    PERM_REQ_CODE
                )
            }

        }
        else {
            showMyLocation()
        }
        getWaypoints().forEach { arr -> run {
            val lat = java.lang.Double.parseDouble(arr[0])
            val lng = java.lang.Double.parseDouble(arr[1])

            showWaypoint(lat, lng)
        }
        }
    }

    private fun showWaypoint(lat: Double, lng: Double): Marker? {

        return mMap.addMarker(
            MarkerOptions().position(LatLng(lat, lng)).icon(
                BitmapDescriptorFactory
                    .defaultMarker(BitmapDescriptorFactory.HUE_RED)
            )
        );
    }

    private fun canAccessLocation() = (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED)

    @SuppressLint("MissingPermission")
    private fun showMyLocation() {
        mMap.isMyLocationEnabled = true
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            PERM_REQ_CODE -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    showMyLocation()
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return
            }
        }
    }

}
