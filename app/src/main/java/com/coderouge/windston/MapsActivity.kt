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
import com.levitnudi.legacytableview.LegacyTableView
import com.levitnudi.legacytableview.LegacyTableView.CENTER
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.hockeyapp.android.CrashManager
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashSet


enum class Offset(val valueMs: Int, val disp: String) {
    ONE_MIN(1 * 60 * 1000, "1m"),
    FIVE_MIN(5 * 60 * 1000, "5m"),
    TEN_MIN(10 * 60 * 1000, "10m"),
    THIRTY_MIN(30 * 60 * 1000, "30m"),
    ONE_H(60 * 60 * 1000, "1h"),
    TWO_H(2 * 60 * 60 * 1000, "2h"),
    SIX_H(6 * 60 * 60 * 1000, "6h"),
    TWELVE_H(12 * 60 * 60 * 1000, "12h"),
    ONE_D(24 * 60 * 60 * 1000, "1d"),
    THREE_D(3 * 24 * 60 * 60 * 1000, "3d"),
    ONE_WEEK(7 * 24 * 60 * 60 * 1000, "1w"),
}


class Info {
    var avgSpeed: Double? = null
    var avgSpeed2: Double? = null
    var avgSpeed3: Double? = null
    var avgBearing: Double? = null


    fun printValues(): Array<String> {
        return arrayOf(rou(avgSpeed, 1), rou(avgSpeed2, 1), rou(avgSpeed3, 1), rou(avgBearing, 0))
    }

    private fun rou(v: Double?, i: Int): String {
        if (v == null) return "-"
        return String.format("%.${i}f", v)
    }
}

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

    private val mReceiver : BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            Log.i(TAG, "receiving broadcasted message")
            refresh()
        }

    }


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


        this.buildInfoText()
    }


    private fun buildInfoText() {


        val table = this.findViewById<LegacyTableView>(R.id.legacy_table_view)
        table.setContentTextSize(40)
        table.setContentTextAlignment(CENTER)
        val offsets = Offset.values()

        val names = arrayOf("offset", "speed (nm/h)", "speed2 (nm/h)", "speed3 (nm/h)", "bearing")

        LegacyTableView.insertLegacyTitle(*names)
        table.setTitle(LegacyTableView.readLegacyTitle())

        val t = statTo()
        val infos = HashMap<Offset, Info>()

        GlobalScope.launch {
            for (o in offsets) {
                val f = Date(t.time - o.valueMs)
                val i = makeInfo(f, t)
                infos.put(o, i)
            }

            for (o in offsets) {
                val i = infos.get(o)
                LegacyTableView.insertLegacyContent(o.disp, *i!!.printValues());
            }

            withContext(Dispatchers.Main) {
                run {
                    table.setContent(LegacyTableView.readLegacyContent())
                    table.build();
                }
            }

        }
    }

    private fun makeInfo(f: Date, t: Date): Info {
        val i = Info()
        i.avgSpeed = calcAverageSpeed(f, t)
        i.avgSpeed2 = calcAverageSpeed2(f, t)
        i.avgSpeed3 = calcAverageSpeed3(f, t)
        i.avgBearing = calcAvgBearing(f, t)
        return i
    }

    private fun onFloatClick() {
        run {
            if (canAccessLocation()) {
                val location = myLocation();

                val lat = location?.latitude
                val lng = location?.longitude


                if (lat != null && lng != null) {
                    addWaypoint(lat, lng, statTo())

                    this.refresh()

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
        this.refresh()
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
            WindstonApp.database.locationDao().insertAll(
                LocationData(
                    lat,
                    lng,
                    date,
                    null,
                    null
                ))
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

        refresh()
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap


        mMap.setOnMapLongClickListener(this)

        mMap.setOnMarkerClickListener { marker ->
            if (marker == mTarget) {
                marker.remove()
                mTarget = null
                refresh()
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

        refresh()
    }

    private fun refresh() {
        refreshMarkers()
        refreshTargetText()
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
            text = "Distance to target" + "\n" + text

        }
        findViewById<TextView>(R.id.distanceToTarget).text = text
    }

    //average of speeds
    private fun calcAverageSpeed(f: Date, t: Date): Double? {
        val averageSpeed = WindstonApp.database.locationDao().averageSpeed(f, t)
        return averageSpeed?.toDouble()
    }

    // (dist / time) between every waypoints
    private fun calcAverageSpeed2(f: Date, t: Date): Double? {
        val locs = WindstonApp.database.locationDao().selectBetween(f, t)

        var res = 0.0

        for ((s, cur) in locs.withIndex()) {
            if (s + 1 >= locs.size) break
            val next = locs.get(s + 1)
            val sp = calcSpeed(cur, next)
            if (sp == null) continue
            res = (res * s + sp) / (s + 1)
        }
        return res
    }

    // (dist_final / time_total)
    private fun calcAverageSpeed3(f: Date, t: Date): Double? {
        val locFrom = WindstonApp.database.locationDao().selectJustAfter(f)
        val locTo = WindstonApp.database.locationDao().selectJustBefore(t)
        if (locFrom == null || locTo == null) return null
        return calcSpeed(locFrom, locTo)
    }

    private fun calcAvgBearing(f: Date, t: Date): Double? {
        val locFrom = WindstonApp.database.locationDao().selectJustAfter(f)
        val locTo = WindstonApp.database.locationDao().selectJustBefore(t)
        if (locFrom == null || locTo == null) return null
        return Utils.bearing(latLng(locFrom), latLng(locTo))
    }

    private fun calcSpeed(
        cur: LocationData,
        next: LocationData
    ): Double? {
        val distance = SphericalUtil.computeDistanceBetween(latLng(cur), latLng(next)) / ONE_NM_IN_M
        val time = (next.date.time - cur.date.time) / 1000.0 / 3600.0
        if (time <=0) return null
        return distance / time
    }

    private fun latLng(cur: LocationData): LatLng {
        return LatLng(cur.lat, cur.lng)
    }

    private fun statTo() = Date()

    private fun statFrom() = Date(0)

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "Lifecycle: on Resume")
        CrashManager.register(this)
        this.refreshMarkers()
        registerReceiver(mReceiver, IntentFilter(ACTION_BROADCAST));
    }


    override fun onPause() {
        super.onPause()
        Log.i(TAG, "Lifecycle: on pause")
        unregisterReceiver(mReceiver);
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
