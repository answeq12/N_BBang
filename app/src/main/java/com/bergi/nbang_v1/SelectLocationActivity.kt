package com.bergi.nbang_v1

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log // Log 임포트 확인
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.naver.maps.geometry.LatLng // Naver 지도 LatLng 임포트 확인
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.OnMapReadyCallback
import com.naver.maps.map.overlay.Marker

class SelectLocationActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private lateinit var naverMap: NaverMap
    private var marker: Marker? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val TAG_SELECT_LOCATION = "SelectLocationActivity" // 로그용 태그

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                getCurrentLocation()
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                getCurrentLocation()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_location)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mapView = findViewById(R.id.map_view)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        val selectLocationButton = findViewById<Button>(R.id.buttonSelectLocation)
        selectLocationButton.setOnClickListener {
            val selectedLatLng = naverMap.cameraPosition.target
            val resultIntent = Intent()

            // 더미 데이터 생성
            val dummyPlaceName = "선택된 위치 (더미)"
            val dummyAddressName = "상세 주소 (더미)"

            resultIntent.putExtra("latitude", selectedLatLng.latitude)
            resultIntent.putExtra("longitude", selectedLatLng.longitude)
            resultIntent.putExtra("placeName", dummyPlaceName) // 더미 장소 이름 전달
            resultIntent.putExtra("addressName", dummyAddressName) // 더미 주소 이름 전달

            Log.d(TAG_SELECT_LOCATION, "Returning location: Lat=${selectedLatLng.latitude}, Lng=${selectedLatLng.longitude}, Place=$dummyPlaceName, Address=$dummyAddressName")

            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }

    override fun onMapReady(naverMap: NaverMap) {
        this.naverMap = naverMap

        marker = Marker()
        marker?.position = naverMap.cameraPosition.target
        marker?.map = naverMap

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
