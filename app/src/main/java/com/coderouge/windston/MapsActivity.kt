package com.coderouge.windston

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.*
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.coderouge.windston.LocationUpdatesService.ACTION_BROADCAST
import com.coderouge.windston.Utils.ONE_NM_IN_M
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.maps.android.SphericalUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.hockeyapp.android.CrashManager
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashSet


class MapsActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMapLongClickListener {

    private var removeMode: Boolean = false
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private lateinit var mMap: GoogleMap
    var mTarget: Marker? = null

    private var mMarkers = HashMap<MarkerKey, Marker>()

    private val PERM_REQ_CODE = 32

    private val DATE_FORMAT = SimpleDateFormat("dd/M/yyyy HH:mm:ss")

    private var mLocationManager : LocationManager? = null


    private val TAG = "MapsActivity"

    private lateinit var mReceiver : BroadcastReceiver1;


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

        this.findViewById<SwitchCompat>(R.id.removeLocation).setOnCheckedChangeListener { view, isChecked ->
            this.removeMode = isChecked
        }
    }


    class BroadcastReceiver1 : BroadcastReceiver {

        private lateinit var activity: MapsActivity

        constructor(ac: MapsActivity) {
            this.activity = ac
        }

        override fun onReceive(p0: Context?, p1: Intent?) {
            Log.i("BroadcastReceiver1", "new marker from service")
            activity.refreshMarkers()
        }

    }


    private fun onFloatClick() {
        run {
            if (canAccessLocation()) {
                val location = myLocation();

                val lat = location?.latitude
                val lng = location?.longitude


                if (lat != null && lng != null) {
                    addWaypoint(lat, lng, Date())

                    this.refreshMarkers()

                    showToast("waypoint added")
                }
                else {
                    showToast("missing location")
                }
            }
            else {
                showToast("cannot access location")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun myLocation() = mLocationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)

    private fun showToast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)

        val item = menu?.findItem(R.id.switch_item);
        val switch = item?.actionView?.findViewById<Switch>(R.id.switchForActionBar)

        switch?.isChecked = isMyServiceRunning(LocationUpdatesService::class.java)

        switch?.setOnCheckedChangeListener { view, isChecked ->

            Intent(this, LocationUpdatesService::class.java).also { intent ->
                if (isChecked) {
                    startService(intent)
                }
                else {
                    showToast("stoping service")
                    stopService(intent)
                }
            }
        }

        return true
    }

    private fun isMyServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.getItemId()) {
            R.id.action_send -> sendEmail()
            R.id.action_purge -> purgeWaypoints()
            R.id.action_settings -> showSettings()
        }
        return true
    }

    private fun showSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun purgeWaypoints() {
        GlobalScope.launch {
            WindstonApp.database.locationDao().purge()
        }
        this.refreshMarkers()
    }

    private fun sendEmail() {
        GlobalScope.launch {
            val bodyText = bodyText()
            if (bodyText.isNullOrEmpty()) {
                showToast("nothing to send")
            }
            else {
                val mailto = "mailto:geopos@coderouge.ovh" +
                        "?subject=" + Uri.encode("Sent from Windston") +
                        "&body=" + Uri.encode(bodyText)

                val emailIntent = Intent(Intent.ACTION_SENDTO)
                emailIntent.data = Uri.parse(mailto)

                try {
                    startActivity(emailIntent)
                } catch (e: ActivityNotFoundException) {
                    //TODO: Handle case where no email app is available
                    showToast("cannot send email")
                }
            }

        }

    }

    private fun bodyText(): String? {
//        val sharedPref: SharedPreferences = getSharedPreferences(PREF_NAME, PRIVATE_MODE)
//        val waypoints = sharedPref.getStringSet(PREF_NAME, HashSet())
//        return join(waypoints, "\n===\n")

        return WindstonApp.database.locationDao().getAll()
            .map { loc -> "latitude: ${loc.lat}\nlongitude: ${loc.lng}\ndate: ${DATE_FORMAT.format(loc.date)}" }
            .joinToString("\n===\n")

    }

    private fun addWaypoint(lat: Double, lng: Double, date: Date) {

        GlobalScope.launch {
            WindstonApp.database.locationDao().insertAll(LocationData(lat, lng, date))
        }

    }

    override fun onMapLongClick(target: LatLng) {

        //creating target
        if (mTarget == null) {
            mTarget = mMap.addMarker(
                MarkerOptions()
                    .position(target)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
            )
        }
        else {
            mTarget!!.position = target
        }

        refreshTargetText()
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap


        mMap.setOnMapLongClickListener(this)

        mMap.setOnMarkerClickListener { marker ->
            if (marker == mTarget) {
                marker.remove()
                mTarget = null
                refreshTargetText()
            }
            else if (this.removeMode) {

                val lat = marker.position.latitude
                val lng = marker.position.longitude

                GlobalScope.launch {
                    val dao = WindstonApp.database.locationDao()
                    val loc = dao.findByLatLng(lat, lng);
                    if (loc.size == 1) {
                        dao.delete(loc.get(0))
                        withContext(Dispatchers.Main) {
                            marker.remove()
                        }
                    }
                }

            }
            else {
                showToast(""+marker.position)
            }
            true
        }

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


        if (canAccessLocation()) {
            val location = mLocationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER);

            val lat = location?.latitude
            val lng = location?.longitude

            if (lat != null && lng != null) {
                mMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(lat, lng),
                        15f
                    )
                )
            }

        }

        refreshMarkers()
    }

    private fun refreshMarkers() {
        GlobalScope.launch {

            //            val tail = WindstonApp.database.locationDao().getTail()
            val allMarkers = WindstonApp.database.locationDao().getAll()

            withContext(Dispatchers.Main) {
                run {
                    val remainingKeys = HashSet(mMarkers.keys)
                    allMarkers.forEach { loc ->

                        val key = markerKey(loc)

                        // is in mMarker => nothing to do
                        if (!remainingKeys.remove(key)) {
                            addMarker(loc)
                        }
                    }

                    for (key in remainingKeys) {
                        removeMarker(key);
                    }
                }
            }
        }
    }

    private suspend fun removeLocation(latitude: Double, longitude: Double) {
        withContext(Dispatchers.IO) {
            run {
                var loc = WindstonApp.database.locationDao().findByLatLng(latitude, longitude);
            }
        }
    }

    private fun refreshTargetText() {
        val target = mTarget?.position
        val myLocation = myLocation()

        var text = ""
        if (myLocation != null && target != null) {
            val my = LatLng(myLocation.latitude, myLocation.longitude)
            val d = SphericalUtil.computeDistanceBetween(my,target) / ONE_NM_IN_M

            val dist = String.format("%.3f nm", d)
            val bearing =  String.format("%.3f deg", Utils.bearing(my,target))
            text = dist + "\n" + bearing

        }
        findViewById<TextView>(R.id.distanceToTarget).text = text
    }

    override fun onResume() {
        super.onResume()
        CrashManager.register(this)

        mReceiver = BroadcastReceiver1(this);
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(mReceiver, IntentFilter(ACTION_BROADCAST));

        this.refreshMarkers()
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(mReceiver);
    }

    private fun addMarker(loc: LocationData) {
        val key = MarkerKey(loc.lat, loc.lng, loc.date);
        if (this.mMarkers.containsKey(key)) {
            Log.i(TAG, "marker key already in array");
        }
        else {
            this.mMarkers.put(key, makeMarker(loc.lat, loc.lng))
        }
    }

    private fun removeMarker(key: MarkerKey) {
        val marker = this.mMarkers.remove(key);
        if (marker != null) {
            marker.remove();
        }
    }

    private fun markerKey(loc: LocationData) = MarkerKey(loc.lat, loc.lng, loc.date)

    private fun makeMarker(lat: Double, lng: Double): Marker {

        return mMap.addMarker(
            MarkerOptions()
                .position(LatLng(lat, lng))
                .icon(
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

private fun Switch?.setOnCheckedChangeListener() {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
}
