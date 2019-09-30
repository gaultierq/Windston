package com.coderouge.windston

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.*
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
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
import com.kunzisoft.switchdatetime.SwitchDateTimeDialogFragment
import com.levitnudi.legacytableview.LegacyTableView
import com.levitnudi.legacytableview.LegacyTableView.CENTER
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.hockeyapp.android.CrashManager
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
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

// ¡™£¢∞§¶•ªº––≠œ∑´´†¥¨ˆˆπ“‘åß∂ƒ©˙∆˚¬…æ«Ω≈ç√∫˜˜≤≥÷
//⁄€‹›ﬁ°·‚—±Œ„´ˇÁ¨ˆØ∏”’ÅÍÎ¸˜Â¯˘¿ÆÚÒÔÓ˝ÅÎ’”∏Ø∏”’
enum class InfoType(val title: String, val format: Int) {
    //    SENSOR_SPEED("sensor", 1),
    AVG_SPEED("avg di/ti", 1),
    SUM_DIST("∑di", 0),
    GLOBAL_SPEED("Df/Tf", 1),
    GLOBAL_DIST("Df", 0),
    SUM_DIST_PROJ("ட ∑di", 0),
    GLOBAL_DIST_PROJ("ட Df", 0),
    AVG_SPEED_PROJ("ட di/ti", 1),
    GLOBAL_SPEED_PROJ("ட Df/Tf", 1),
    BEARING("bearing", 0),
}


class Info {

    val values : TreeMap<InfoType, Double?> = TreeMap()

    companion object {
        fun round(v: Double?, it: InfoType): String {
            return rou(v, it.format)
        }

        private fun rou(v: Double?, i: Int): String {
            if (v == null) return "-"
            return String.format("%.${i}f", v)
        }
    }

    fun setValue(type: InfoType, value: Double?) {
        this.values.put(type, value)
    }

    fun printValues(): Array<String> {
        return this.values.entries.map { (k, v) -> rou(v, k.format) }.toTypedArray()
    }


}

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMapLongClickListener {

    private val PERM_REQ_CODE = 32
    private val DATE_FORMAT = SimpleDateFormat("dd/M/yyyy HH:mm:ss")
    private val FILTER_DISTANCE_STEP = arrayOf(0, 10, 100, 300, 500, 1000, 10000, 50000, 100000)
    private val TAG = "MapsActivity"

    private var removeMode: Boolean = false
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private lateinit var mMap: GoogleMap
    private var mTarget: Marker? = null
    private var mMarkers = HashMap<MarkerKey, Marker>()
    private var mLocationManager : LocationManager? = null
    private var filterMinDistMeters = 0
    private val mReceiver : BroadcastReceiver = createReceiver()

    private fun createReceiver(): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                Log.i(TAG, "receiving broadcasted message")
                refresh()
            }
        }
    }

    @SuppressLint("NewApi")
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

        configureChangeLocation()

        this.createInfoTable()

        findViewById<TextView>(R.id.lastSentDate).setOnClickListener { displayLastSentDialog() }
        refreshMinDate()

        configureTargetBearing()
        refreshTargetBearing()
    }

    private fun configureTargetBearing() {
        findViewById<EditText>(R.id.targetBearing).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun afterTextChanged(p0: Editable?) {
                val value = getDisplayedTargetBearing()
                Utils.writeTargetBearing(this@MapsActivity, value)
                refresh()
            }

        })
    }

    private fun getDisplayedTargetBearing() = findViewById<TextView>(R.id.targetBearing).text.toString().toLongOrNull()


    private fun refreshTargetBearing() {
        val tb = Utils.readTargetBearing(this)
        if (tb != getDisplayedTargetBearing()) {
            findViewById<EditText>(R.id.targetBearing).setText(tb?.toString())
        }
    }

    private fun configureChangeLocation() {
        this.findViewById<SwitchCompat>(R.id.removeLocation).setOnCheckedChangeListener { view, isChecked ->
            this.removeMode = isChecked
        }
        this.findViewById<SeekBar>(R.id.filterDistance)
            .setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(p0: SeekBar?) {
                }

                override fun onStopTrackingTouch(p0: SeekBar?) {
                }

                override fun onProgressChanged(view: SeekBar?, progress: Int, fromUser: Boolean) {

                    //persist
                    filterMinDistMeters = FILTER_DISTANCE_STEP[progress]
                    findViewById<TextView>(R.id.filterDistanceText).text = "Filter distance (" + filterMinDistMeters + "m)"
                    refreshMarkers()
                }
            })
    }

    private fun refreshMinDate() {
        findViewById<TextView>(R.id.lastSentDate).setText(Utils.readLastSentDate(this).toString())
    }

    private fun displayLastSentDialog() {
        // Initialize
        val dateTimeDialogFragment = SwitchDateTimeDialogFragment.newInstance(
            "Last Sent Date",
            "OK",
            "Cancel"
        );

        // Assign values
        dateTimeDialogFragment.startAtCalendarView();
        dateTimeDialogFragment.set24HoursMode(true);
        dateTimeDialogFragment.setDefaultDateTime(Utils.readLastSentDate(this));

        // Define new day and month format
        try {
            dateTimeDialogFragment.setSimpleDateMonthAndDayFormat(SimpleDateFormat("dd MMMM", Locale.getDefault()));
        } catch (e: SwitchDateTimeDialogFragment.SimpleDateMonthAndDayFormatException) {
            Log.e(TAG, e.message);
        }

        // Set listener
        dateTimeDialogFragment.setOnButtonClickListener(object : SwitchDateTimeDialogFragment.OnButtonClickListener {
            override fun onPositiveButtonClick(date: Date?) {
                Utils.writeLastSentDate(this@MapsActivity, date)
                refresh()
                findViewById<TextView>(R.id.lastSentDate).setText(date.toString())
            }

            override fun onNegativeButtonClick(date: Date?) {

            }
        });

        // Show
        dateTimeDialogFragment.show(getSupportFragmentManager(), "dialog_time");
    }


    private fun createInfoTable() {


        val table = this.findViewById<LegacyTableView>(R.id.legacy_table_view)
        table.setContentTextSize(40)
        table.setContentTextAlignment(CENTER)
        table.setTitleTextAlignment(CENTER)
        table.setTitleTextSize(40)

        //TODO: check order
        val offsets = Offset.values()

        GlobalScope.launch {
            val infos = readInfos(offsets)


            val values = TreeMap<InfoType, TreeMap<Offset, Double?>>()
            var cCount = 0
            for ((k,info) in infos) {

                for (type in InfoType.values()) {
                    val v = info.values.get(type)
                    if (!values.containsKey(type)) values.put(type, TreeMap())
                    val ll = values.get(type)!!
                    ll.put(k, v)
                    if (ll.size > cCount) cCount  = ll.size
                }
            }
            val offf = TreeSet<Offset>()
            for ((k, v) in values) {
                offf.addAll(v.keys)
                LegacyTableView.insertLegacyContent(k.title, *v.values.map { vv -> Info.round(vv, k)}.toTypedArray());
            }

            withContext(Dispatchers.Main) {
                run {

                    LegacyTableView.insertLegacyTitle("type", *offf.map { v ->  v.disp}.toTypedArray())
                    table.setTitle(LegacyTableView.readLegacyTitle())

                    table.setContent(LegacyTableView.readLegacyContent())
                    table.build();
                }
            }

        }
    }

    private fun readInfos(offsets: Array<Offset>): HashMap<Offset, Info> {
        val t = statTo()
        val infos = HashMap<Offset, Info>()

        var prev: Offset? = null

        fun offseted(o: Offset) = Date(t.time - o.valueMs)

        for (o in offsets) {
            val from = offseted(o)

            if (prev != null) {

                val to = offseted(prev)
                val cb = WindstonApp.database.locationDao().countBetween(from, to)
                if (cb == 0) {
                    //no data between this offset and the prev with offset
                    continue
                }
            }

            infos.put(o, makeInfo(from, t))
            prev = o
        }
        return infos
    }

    private fun makeInfo(f: Date, t: Date): Info {
        val i = Info()
//        i.setValue(InfoType.SENSOR_SPEED, calcSensorSpeed(f, t))
        i.setValue(InfoType.AVG_SPEED, Companion.calcAverageSpeed(f, t))
        i.setValue(InfoType.GLOBAL_SPEED, Companion.calcGlobalSpeed(f, t))

        i.setValue(InfoType.GLOBAL_DIST, Companion.calcGlobalDist(f, t))
        i.setValue(InfoType.SUM_DIST, Companion.calcSumDist(f, t))

        val readTargetBearing = Utils.readTargetBearing(this)
        if (readTargetBearing != null) {
            val tbd = readTargetBearing.toDouble()
            i.setValue(InfoType.AVG_SPEED_PROJ, Companion.calcAverageSpeed(f, t, tbd))
            i.setValue(InfoType.GLOBAL_SPEED_PROJ, Companion.calcGlobalSpeed(f, t, tbd))

            i.setValue(InfoType.GLOBAL_DIST_PROJ, Companion.calcGlobalDist(f, t, tbd))
            i.setValue(InfoType.SUM_DIST_PROJ, Companion.calcSumDist(f, t, tbd))

        }
        i.setValue(InfoType.BEARING, Companion.calcAvgBearing(f, t))
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
            R.id.action_options-> toggleOptions()
        }
        return true
    }

    private fun showSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun toggleOptions() {
        Utils.writeOptionsOpened(this, !Utils.readOptionsOpened(this))
        refresh()
    }

    private fun refreshOptionsVisibility() {
        findViewById<View>(R.id.options).visibility = if (Utils.readOptionsOpened(this)) View.VISIBLE else View.GONE
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

        return selectFilterMarkers()
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
            else {

                val lat = marker.position.latitude
                val lng = marker.position.longitude

                GlobalScope.launch {
                    val dao = WindstonApp.database.locationDao()
                    val loc = dao.findByLatLng(lat, lng);
                    if (this@MapsActivity.removeMode) {
                        if (loc.size == 1) {
                            dao.delete(loc.get(0))
                            withContext(Dispatchers.Main) {
                                refresh()
                            }
                        }
                    }
                    else {
                        withContext(Dispatchers.Main) {
                            showToast(""+ loc)
                        }

                    }

                }

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
        refreshTargetBearing()
        refreshOptionsVisibility()

    }

    private fun refreshMarkers() {
        GlobalScope.launch {

            val filteredMarkers = selectFilterMarkers()


            withContext(Dispatchers.Main) {
                run {
                    val remainingKeys = HashSet(mMarkers.keys)
                    filteredMarkers.forEach { loc ->

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

    private fun selectFilterMarkers(): ArrayList<LocationData> {

        val minDate = Utils.readLastSentDate(this)
        val allMarkers = WindstonApp.database.locationDao().getAllAfter(minDate)

        //filtering
        val filteredMarkers = ArrayList<LocationData>()
        val minDistNm = this.filterMinDistMeters / Utils.ONE_NM_IN_M.toDouble()


        var last: LocationData? = null
        //filter
        fun addIt(m: LocationData) {
            filteredMarkers.add(m)
            last = m
        }

        for (m in allMarkers) {
            if (last == null || Companion.milesBetween(m, last!!) > minDistNm) {
                addIt(m)
            }
        }
        return filteredMarkers
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

    companion object {


        private fun latLng(cur: LocationData): LatLng {
            return LatLng(cur.lat, cur.lng)
        }

        private fun calcDist(
            left: LocationData,
            right: LocationData,
            proj: Double?
        ): Double {
            var distance = SphericalUtil.computeDistanceBetween(Companion.latLng(left), Companion.latLng(right)) / ONE_NM_IN_M

            if (proj != null) {
                val b = Utils.bearing(Companion.latLng(left), Companion.latLng(right))
                val diff = (360 + proj - b) % 180
                distance *= Math.cos(Math.toRadians(diff));
            }
            return distance
        }

        private fun calcSpeed(
            left: LocationData,
            right: LocationData,
            proj: Double? = null
        ): Double? {
            val distance = Companion.calcDist(left, right, proj)

            val time = (right.date.time - left.date.time) / 1000.0 / 3600.0
            if (time <=0) return null
            return distance / time
        }

        private fun calcAvgBearing(f: Date, t: Date): Double? {
            val locFrom = WindstonApp.database.locationDao().selectJustAfter(f)
            val locTo = WindstonApp.database.locationDao().selectJustBefore(t)
            if (locFrom == null || locTo == null) return null
            return Utils.bearing(Companion.latLng(locFrom), Companion.latLng(locTo))
        }

        // (dist_final / time_total)
        private fun calcGlobalDist(f: Date, t: Date, proj: Double? = null): Double? {
            val locFrom = WindstonApp.database.locationDao().selectJustAfter(f)
            val locTo = WindstonApp.database.locationDao().selectJustBefore(t)
            if (locFrom == null || locTo == null) return null
            return Companion.calcDist(locFrom, locTo, proj)
        }

        // (dist_final / time_total)
        private fun calcGlobalSpeed(f: Date, t: Date, proj: Double? = null): Double? {
            val locFrom = WindstonApp.database.locationDao().selectJustAfter(f)
            val locTo = WindstonApp.database.locationDao().selectJustBefore(t)
            if (locFrom == null || locTo == null) return null
            return Companion.calcSpeed(locFrom, locTo, proj)
        }

        // (dist / time) between every waypoints
        private fun calcSumDist(f: Date, t: Date, proj: Double? = null): Double? {
            val locs = WindstonApp.database.locationDao().selectBetween(f, t)

            var res = 0.0

            for ((s, cur) in locs.withIndex()) {
                if (s + 1 >= locs.size) break
                val next = locs.get(s + 1)
                val dist = Companion.calcDist(cur, next, proj)
                res += dist
            }
            return res
        }

        // (dist / time) between every waypoints
        private fun calcAverageSpeed(f: Date, t: Date, proj: Double? = null): Double? {
            val locs = WindstonApp.database.locationDao().selectBetween(f, t)

            var res = 0.0

            for ((s, cur) in locs.withIndex()) {
                if (s + 1 >= locs.size) break
                val next = locs.get(s + 1)
                val sp = Companion.calcSpeed(cur, next, proj)
                if (sp == null) continue
                res = (res * s + sp) / (s + 1)
            }
            return res
        }

        //average of speeds
        private fun calcSensorSpeed(f: Date, t: Date): Double? {
            val averageSpeed = WindstonApp.database.locationDao().averageSpeed(f, t)
            return averageSpeed?.toDouble()
        }

        private fun milesBetween(
            left: LocationData,
            right: LocationData
        ) = SphericalUtil.computeDistanceBetween(LatLng(left.lat, left.lng), LatLng(right.lat, right.lng)) / ONE_NM_IN_M

    }

}

private fun Switch?.setOnCheckedChangeListener() {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
}
