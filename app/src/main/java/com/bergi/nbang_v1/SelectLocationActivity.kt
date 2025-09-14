package com.bergi.nbang_v1

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.OnMapReadyCallback
import com.naver.maps.map.overlay.Marker
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SelectLocationActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private lateinit var naverMap: NaverMap
    private var marker: Marker? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var naverApiService: NaverApiService

    private val TAG_SELECT_LOCATION = "SelectLocationActivity"
    private val NAVER_API_BASE_URL = "https://naveropenapi.apigw.ntruss.com/"
    private val CLIENT_ID = "t0no2o6lq3" // 실제 클라이언트 ID로 교체 필요
    private val CLIENT_SECRET = "5Uaoa2ZEMlG2aUvLODs2w4yY5z9bof3d2p2jT5Vf" // 실제 클라이언트 시크릿으로 교체 필요

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> getCurrentLocation()
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> getCurrentLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_location)

        initRetrofit()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mapView = findViewById(R.id.map_view)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        val selectLocationButton = findViewById<Button>(R.id.buttonSelectLocation)
        selectLocationButton.setOnClickListener {
            val selectedLatLng = naverMap.cameraPosition.target
            reverseGeocode(selectedLatLng)
        }
    }

    private fun initRetrofit() {
        val retrofit = Retrofit.Builder()
            .baseUrl(NAVER_API_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        naverApiService = retrofit.create(NaverApiService::class.java)
    }

    private fun reverseGeocode(latLng: LatLng) {
        val coords = "${latLng.longitude},${latLng.latitude}"
        naverApiService.reverseGeocode(CLIENT_ID, CLIENT_SECRET, coords)
            .enqueue(object : Callback<ReverseGeocodingResponse> {
                override fun onResponse(
                    call: Call<ReverseGeocodingResponse>,
                    response: Response<ReverseGeocodingResponse>
                ) {
                    if (response.isSuccessful && response.body() != null && response.body()!!.results.isNotEmpty()) {
                        val result = response.body()!!.results[0]
                        val region = result.region
                        val land = result.land
                        val placeName = land.name.ifEmpty { "${region.area2.name} ${region.area3.name}" }
                        val addressName = "${region.area1.name} ${region.area2.name} ${region.area3.name} ${land.number1}".trim()
                        returnResult(latLng, placeName, addressName)
                    } else {
                        Log.w(TAG_SELECT_LOCATION, "Reverse geocoding failed: ${response.errorBody()?.string()}")
                        returnResult(latLng, "미정", "미정")
                    }
                }

                override fun onFailure(call: Call<ReverseGeocodingResponse>, t: Throwable) {
                    Log.e(TAG_SELECT_LOCATION, "Reverse geocoding network error", t)
                    Toast.makeText(applicationContext, "주소 변환에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    returnResult(latLng, "미정", "미정")
                }
            })
    }

    private fun returnResult(latLng: LatLng, placeName: String, addressName: String) {
        val resultIntent = Intent().apply {
            putExtra("latitude", latLng.latitude)
            putExtra("longitude", latLng.longitude)
            putExtra("placeName", placeName)
            putExtra("addressName", addressName)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    override fun onMapReady(naverMap: NaverMap) {
        this.naverMap = naverMap
        marker = Marker().apply {
            position = naverMap.cameraPosition.target
            map = naverMap
        }
        checkLocationPermission()
        naverMap.addOnCameraChangeListener { _, _ ->
            marker?.position = naverMap.cameraPosition.target
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            getCurrentLocation()
        } else {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            val cameraUpdate = CameraUpdate.scrollTo(LatLng(37.5665, 126.9780)) // Seoul
            naverMap.moveCamera(cameraUpdate)
            return
        }
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                val targetLocation = if (location != null) {
                    LatLng(location.latitude, location.longitude)
                } else {
                    LatLng(37.5665, 126.9780) // Default to Seoul
                }
                val cameraUpdate = CameraUpdate.scrollTo(targetLocation)
                naverMap.moveCamera(cameraUpdate)
            }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }
}
