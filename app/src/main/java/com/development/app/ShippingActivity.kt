package com.development.app

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.bumptech.glide.Glide
import com.development.app.Common.Common
import com.development.app.Model.ShippingOrderModel
import com.development.app.remote.IGoogleApi
import com.development.app.remote.RetrofitClient
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import io.paperdb.Paper
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_shipping.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

class ShippingActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private var shipperMarker: Marker? = null
    private var shippingOrderModel: ShippingOrderModel? = null

    var isInit = false
    var previousLocation: Location? = null

    private var handler: Handler? = null
    private var index: Int = -1
    private var next: Int = 0
    private var startPosition: LatLng? = LatLng(0.0, 0.0)
    private var endPosition: LatLng? = LatLng(0.0, 0.0)
    private var v: Float = 0f
    private var lat: Double = -1.0
    private var lng: Double = -1.0

    private var blackPolyline: Polyline? = null
    private var greyPolyline: Polyline? = null
    private var polylineOptions: PolylineOptions? = null
    private var blackPolylineOptions: PolylineOptions? = null
    private var redPolyline: Polyline? = null
    private var greenPolyline: Polyline? = null

    private var polylineList: List<LatLng> = ArrayList<LatLng>()
    private var iGoogleApi: IGoogleApi? = null
    private var compositeDisposable = CompositeDisposable()

    private lateinit var places_fragment: AutocompleteSupportFragment
    private lateinit var placesClient: PlacesClient
    private val placeFields = Arrays.asList(
        Place.Field.ID,
        Place.Field.NAME,
        Place.Field.ADDRESS,
        Place.Field.LAT_LNG
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shipping)

        iGoogleApi = RetrofitClient.instance!!.create(IGoogleApi::class.java)
        initPlaces()
        setupPlaceAutocomplete()

        buildLocationRequest()
        buildLocationCallback()


        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )


        Dexter.withActivity(this)
            .withPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
            .withListener(object : PermissionListener {
                override fun onPermissionGranted(response: PermissionGrantedResponse?) {
                    // Obtain the SupportMapFragment and get notified when the map is ready to be used.
                    val mapFragment = supportFragmentManager
                        .findFragmentById(R.id.map) as SupportMapFragment
                    mapFragment.getMapAsync(this@ShippingActivity)

                    fusedLocationProviderClient =
                        LocationServices.getFusedLocationProviderClient(this@ShippingActivity)
                    fusedLocationProviderClient.requestLocationUpdates(
                        locationRequest,
                        locationCallback,
                        Looper.myLooper()
                    )
                }

                override fun onPermissionRationaleShouldBeShown(
                    permission: PermissionRequest?,
                    token: PermissionToken?
                ) {

                }

                override fun onPermissionDenied(response: PermissionDeniedResponse?) {
                    Toast.makeText(
                        this@ShippingActivity,
                        "Please Enable location services",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            }).check()

        initViews()

    }

    private fun setupPlaceAutocomplete() {
        places_fragment = supportFragmentManager
            .findFragmentById(R.id.places_autocomplete_fragment) as AutocompleteSupportFragment
        places_fragment.setPlaceFields(placeFields)
        places_fragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
               drawRoutes(place)
            }

            override fun onError(p0: Status) {
                Toast.makeText(this@ShippingActivity, "" + p0.statusMessage, Toast.LENGTH_SHORT)
                    .show()
            }

        })
    }

    private fun initPlaces() {
        Places.initialize(this, getString(R.string.google_maps_key))
        placesClient = Places.createClient(this)
    }

    private fun initViews() {
        btn_start_trip.setOnClickListener {
            val data = Paper.book().read<String>(Common.SHIPPING_DATA)
            Paper.book().write(Common.TRIP_START, data)
            btn_start_trip.isEnabled = false // disable after started trip

            fusedLocationProviderClient.lastLocation.addOnSuccessListener { location ->
                val update_data = HashMap<String, Any>()
                update_data.put("currentLat", location.latitude)
                update_data.put("currentLng", location.longitude)

                FirebaseDatabase.getInstance()
                    .getReference(Common.SHIPPING_ORDER_REF)
                    .child(shippingOrderModel!!.key!!)
                    .updateChildren(update_data)
                    .addOnFailureListener { e->
                        Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
                    }
                    .addOnSuccessListener { aVoid->
                        //show direction from staff app after click start trip
                        drawRoutes(data)
                    }
            }

        }

        btn_show.setOnClickListener {
            if (expandable_layout.isExpanded)
                btn_show.visibility = View.GONE
            else
                expandable_layout.toggle()

        }
        btn_hide.setOnClickListener {
            if (expandable_layout.isExpanded)
                expandable_layout.toggle()

            else
                expandable_layout.toggle()

        }

    }

    private fun setShippingOrderModel() {

        Paper.init(this)
        var data: String? = ""
        if (TextUtils.isEmpty(Paper.book().read(Common.TRIP_START))) {
            data = Paper.book().read<String>(Common.SHIPPING_DATA)
            btn_start_trip.isEnabled = true
        } else {
            data = Paper.book().read<String>(Common.TRIP_START)
            btn_start_trip.isEnabled = false
        }
        if (!TextUtils.isEmpty(data)) {

            drawRoutes(data)
            shippingOrderModel = Gson().fromJson<ShippingOrderModel>(
                data,
                object : TypeToken<ShippingOrderModel>() {}.type
            )

            if (shippingOrderModel != null) {
                Common.setSpanStringColor(
                    "",
                    shippingOrderModel!!.orderModel!!.userName,
                    txt_name,
                    Color.parseColor("#000000")
                )
                Common.setSpanStringColor(
                    "",
                    shippingOrderModel!!.orderModel!!.shippingAddress,
                    txt_address,
                    Color.parseColor("#000000")
                )
                Common.setSpanStringColor(
                    "NO : ",
                    shippingOrderModel!!.orderModel!!.key,
                    txt_order_number,
                    Color.parseColor("#000000")
                )

                txt_date!!.text = StringBuilder().append(
                    SimpleDateFormat("dd-MM-yyyy, HH:mm").format(
                        shippingOrderModel!!.orderModel!!.createDate
                    )
                )

                Glide.with(this)
                    .load(shippingOrderModel!!.orderModel!!.cartItemList!![0]!!.foodImage)
                    .into(img_food_image)
            }
        } else {
            Toast.makeText(this, "Shipping Order Model is null", Toast.LENGTH_SHORT).show()
        }

    }

    private fun drawRoutes(place: Place) {

        mMap.addMarker(MarkerOptions()
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))
                .title(place.name)
                .snippet(place.address)
                .position(place.latLng!!)
                )


        fusedLocationProviderClient.lastLocation
            .addOnFailureListener { e ->
                Toast.makeText(
                    this@ShippingActivity, "" + e.message,
                    Toast.LENGTH_SHORT
                ).show()
            }

            .addOnSuccessListener { location ->
                val to = StringBuilder().append(place.latLng!!.latitude)
                    .append(", ")
                    .append(place.latLng!!.longitude).toString()

                val from = StringBuilder().append(location.latitude)
                    .append(",")
                    .append(location.longitude)
                    .toString()

                compositeDisposable.add(
                    iGoogleApi!!.getDirections(
                        "driving", "less_driving",
                        from, to,
                        getString(R.string.google_maps_key)
                    )
                    !!.subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ s ->
                            try {
                                val jsonObject = JSONObject(s)
                                val jsonArray = jsonObject.getJSONArray("routes")
                                for (i in 0 until jsonArray.length()) {
                                    val route = jsonArray.getJSONObject(i)
                                    val poly = route.getJSONObject("overview_polyline")
                                    val polyline = poly.getString("points")
                                    polylineList = Common.decodePoly(polyline)
                                }

                                polylineOptions = PolylineOptions()
                                polylineOptions!!.color(Color.BLUE)
                                polylineOptions!!.width(12.0f)
                                polylineOptions!!.startCap(SquareCap())
                                polylineOptions!!.endCap(SquareCap())
                                polylineOptions!!.jointType(JointType.ROUND)
                                polylineOptions!!.addAll(polylineList)
                                greenPolyline = mMap.addPolyline(polylineOptions)


                            } catch (e: Exception) {
                                Log.d("Debug", e.message)
                            }
                        }, { throwable ->
                            Toast.makeText(
                                this@ShippingActivity,
                                "" + throwable.message,
                                Toast.LENGTH_SHORT
                            ).show()
                        })
                )

            }
    }

    private fun drawRoutes(data: String?) {
        val shippingOrderModel = Gson()
            .fromJson<ShippingOrderModel>(data, object : TypeToken<ShippingOrderModel>() {}.type)

        mMap.addMarker(
            MarkerOptions()
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.box))
                .title(shippingOrderModel.orderModel!!.userName)
                .snippet(shippingOrderModel.orderModel!!.shippingAddress)
                .position(
                    LatLng(
                        shippingOrderModel.orderModel!!.lat,
                        shippingOrderModel.orderModel!!.lng
                    )
                )
        )

        fusedLocationProviderClient.lastLocation
            .addOnFailureListener { e ->
                Toast.makeText(
                    this@ShippingActivity, "" + e.message,
                    Toast.LENGTH_SHORT
                ).show()
            }

            .addOnSuccessListener { location ->
                val to = StringBuilder().append(shippingOrderModel.orderModel!!.lat)
                    .append(", ")
                    .append(shippingOrderModel.orderModel!!.lng).toString()

                val from = StringBuilder().append(location.latitude)
                    .append(",")
                    .append(location.longitude)
                    .toString()

                compositeDisposable.add(
                    iGoogleApi!!.getDirections(
                        "driving", "less_driving",
                        from, to,
                        getString(R.string.google_maps_key)
                    )
                    !!.subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ s ->
                            try {
                                val jsonObject = JSONObject(s)
                                val jsonArray = jsonObject.getJSONArray("routes")
                                for (i in 0 until jsonArray.length()) {
                                    val route = jsonArray.getJSONObject(i)
                                    val poly = route.getJSONObject("overview_polyline")
                                    val polyline = poly.getString("points")
                                    polylineList = Common.decodePoly(polyline)
                                }

                                polylineOptions = PolylineOptions()
                                polylineOptions!!.color(Color.BLACK)
                                polylineOptions!!.width(12.0f)
                                polylineOptions!!.startCap(SquareCap())
                                polylineOptions!!.endCap(SquareCap())
                                polylineOptions!!.jointType(JointType.ROUND)
                                polylineOptions!!.addAll(polylineList)
                                redPolyline = mMap.addPolyline(polylineOptions)


                            } catch (e: Exception) {
                                Log.d("Debug", e.message)
                            }
                        }, { throwable ->
                            Toast.makeText(
                                this@ShippingActivity,
                                "" + throwable.message,
                                Toast.LENGTH_SHORT
                            ).show()
                        })
                )

            }
    }

    private fun buildLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult?) {
                super.onLocationResult(p0)
                val locationShipper =
                    LatLng(p0!!.lastLocation.latitude, p0!!.lastLocation.longitude)

                updateLocation(p0.lastLocation)

                if (shipperMarker == null) {
                    val height = 80
                    val width = 80
                    val bitmapDrawable =
                        ContextCompat.getDrawable(this@ShippingActivity, R.drawable.shipper2)
                    val b = bitmapDrawable!!.toBitmap()
                    val smallMarker = Bitmap.createScaledBitmap(b, width, height, false)
                    shipperMarker = mMap!!.addMarker(
                        MarkerOptions()
                            .icon(BitmapDescriptorFactory.fromBitmap(smallMarker))
                            .position(locationShipper)
                            .title("You")
                    )
                    mMap!!.animateCamera(CameraUpdateFactory.newLatLngZoom(locationShipper, 18f))
                }


                if (isInit && previousLocation != null) {

                    val from = StringBuilder()
                        .append(previousLocation!!.latitude)
                        .append(",")
                        .append(previousLocation!!.longitude)
                    val to = StringBuilder()
                        .append(locationShipper.latitude)
                        .append(",")
                        .append(locationShipper.longitude)
                    moveMarkerAnimation(shipperMarker, from, to)
                    previousLocation = p0.lastLocation
                }

                if (!isInit) {

                    isInit = true
                    previousLocation = p0.lastLocation
                }
            }
        }
    }

    private fun updateLocation(lastLocation: Location?) {
        val update_data = HashMap<String, Any>()
        update_data.put("currentLat", lastLocation!!.latitude)
        update_data.put("currentLng", lastLocation!!.longitude)

        val data = Paper.book().read<String>(Common.TRIP_START)
        if (!TextUtils.isEmpty(data))
        {
            val shippingOrder = Gson().fromJson<ShippingOrderModel>(data, object:TypeToken<ShippingOrderModel>(){}.type)
            if (shippingOrder != null)
            {
                FirebaseDatabase.getInstance()
                    .getReference(Common.SHIPPING_ORDER_REF)
                    .child(shippingOrder.key!!)
                    .updateChildren(update_data)
                    .addOnFailureListener { e-> Toast.makeText(this, ""+e.message, Toast.LENGTH_SHORT).show() }
            }
        }
        else
        {
            Toast.makeText(this, "Please enter start trip", Toast.LENGTH_SHORT).show()
        }

    }

    private fun moveMarkerAnimation(
        marker: Marker?,
        from: StringBuilder,
        to: StringBuilder
    ) {
        compositeDisposable.add(
            iGoogleApi!!.getDirections(
                "driving",
                "less_driving",
                from.toString(),
                to.toString(),
                getString(R.string.google_maps_key)
            )
            !!.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ s ->
                    Log.d("DEBUG", s)
                    try {
                        val jsonObject = JSONObject(s)
                        val jsonArray = jsonObject.getJSONArray("routes")
                        for (i in 0 until jsonArray.length()) {
                            val route = jsonArray.getJSONObject(i)
                            val poly = route.getJSONObject("overview_polyline")
                            val polyline = poly.getString("points")
                            polylineList = Common.decodePoly(polyline)
                        }

                        polylineOptions = PolylineOptions()
                        polylineOptions!!.color(Color.GRAY)
                        polylineOptions!!.width(5.0f)
                        polylineOptions!!.startCap(SquareCap())
                        polylineOptions!!.endCap(SquareCap())
                        polylineOptions!!.jointType(JointType.ROUND)
                        polylineOptions!!.addAll(polylineList)
                        greyPolyline = mMap.addPolyline(polylineOptions)

                        blackPolylineOptions = PolylineOptions()
                        blackPolylineOptions!!.color(Color.GRAY)
                        blackPolylineOptions!!.width(5.0f)
                        blackPolylineOptions!!.startCap(SquareCap())
                        blackPolylineOptions!!.endCap(SquareCap())
                        blackPolylineOptions!!.jointType(JointType.ROUND)
                        blackPolylineOptions!!.addAll(polylineList)
                        blackPolyline = mMap.addPolyline(blackPolylineOptions)

                        //animator
                        val polylineAnimator = ValueAnimator.ofInt(0, 100)
                        polylineAnimator.setDuration(2000)
                        polylineAnimator.setInterpolator(LinearInterpolator())
                        polylineAnimator.addUpdateListener { valueAnimator ->
                            val points = greyPolyline!!.points
                            val precentValue =
                                Integer.parseInt(valueAnimator.animatedValue.toString())
                            val size = points.size
                            val newPoints = (size * (precentValue / 100.0f).toInt())
                            val p = points.subList(0, newPoints)
                            blackPolyline!!.points = p
                        }

                        polylineAnimator.start()

                        // car moving
                        index = -1
                        next = 1
                        val r = object : Runnable {
                            override fun run() {
                                if (index < polylineList.size - 1) {
                                    index++
                                    next = index + 1
                                    startPosition = polylineList[index]
                                    endPosition = polylineList[next]
                                }
                                val valueAnimator = ValueAnimator.ofInt(0, 1)
                                valueAnimator.setDuration(1500)
                                valueAnimator.setInterpolator(LinearInterpolator())
                                valueAnimator.addUpdateListener { valueAnimator ->
                                    v = valueAnimator.animatedFraction
                                    lat =
                                        v * endPosition!!.latitude + (1 - v) * startPosition!!.latitude
                                    lng =
                                        v * endPosition!!.longitude + (1 - v) * startPosition!!.longitude

                                    val newPos = LatLng(lat, lng)
                                    marker!!.position = newPos
                                    marker!!.setAnchor(0.5f, 0.5f)
                                    marker!!.rotation = Common.getBearing(startPosition!!, newPos)

                                    mMap.moveCamera(CameraUpdateFactory.newLatLng(marker.position))
                                }

                                valueAnimator.start()
                                if (index < polylineList.size - 2)
                                    handler!!.postDelayed(this, 1500)
                            }

                        }

                        handler = Handler()
                        handler!!.postDelayed(r, 1500)


                    } catch (e: Exception) {
                        Log.d("Debug", e.message)
                    }
                }, { throwable ->
                    Toast.makeText(
                        this@ShippingActivity,
                        "" + throwable.message,
                        Toast.LENGTH_SHORT
                    ).show()
                })
        )
    }


    private fun buildLocationRequest() {
        locationRequest = LocationRequest()
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        locationRequest.setInterval(2000) //15000
        locationRequest.setFastestInterval(50000)//10000
        locationRequest.setSmallestDisplacement(20f)//20f
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        setShippingOrderModel()
        mMap.uiSettings.isCompassEnabled = true
        mMap!!.uiSettings.isZoomControlsEnabled = true
        try {
            val sucess = googleMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                    this,
                    R.raw.uber_style2
                )
            )
            greetings()
            if (!sucess)
                Log.d("Google", "Failed to load map Style")
        } catch (ex: Resources.NotFoundException) {
            Log.d("Google", "Not found json string for map style")
        }

    }

    private fun greetings(): String {

        val c = Calendar.getInstance()
        val timeOfDay = c.get(Calendar.HOUR_OF_DAY)

        return when (timeOfDay) {
            in 0..12 -> txt_greeting.setText("Good morning!").toString()
            in 13..15 -> txt_greeting.setText("Good afternoon!").toString()
            in 16..19 -> txt_greeting.setText("Good evening!").toString()
            in 20..0 -> txt_greeting.setText("Good night!").toString()
            else -> {
                "Hello " + Common.currentShipperUser!!.name + "!"
            }
        }
    }

    override fun onDestroy() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        compositeDisposable.clear()
        super.onDestroy()
    }
}
