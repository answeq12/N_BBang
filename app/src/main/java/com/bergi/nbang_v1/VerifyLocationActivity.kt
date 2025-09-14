package com.bergi.nbang_v1

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.ktx.Firebase
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.*
import com.naver.maps.map.overlay.Marker
import java.util.*

class VerifyLocationActivity : AppCompatActivity(), OnMapReadyCallback {

    // ... (변수 선언은 그대로) ...
    private lateinit var locationTextView: TextView
    private lateinit var confirmButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var mapView: MapView
    private lateinit var naverMap: NaverMap

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var db: FirebaseFirestore
    private var currentLocationString: String? = null
    private var currentLocation: Location? = null
    private val TAG = "VerifyLocationActivity"

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // [수정] 권한 획득 후 바로 위치 가져오기
            getCurrentLocation()
        } else {
            Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify_location)

        // ... (뷰, fusedLocationClient, db 초기화는 그대로) ...
        locationTextView = findViewById(R.id.textViewCurrentLocation)
        confirmButton = findViewById(R.id.buttonConfirmLocation)
        progressBar = findViewById(R.id.progressBar)
        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this) // onMapReady 콜백을 요청

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        db = FirebaseFirestore.getInstance()

        confirmButton.setOnClickListener {
            saveLocationToFirestore()
        }
    }

    override fun onMapReady(naverMap: NaverMap) {
        this.naverMap = naverMap

        // 지도 UI 설정 (조작 비활성화)
        val uiSettings = naverMap.uiSettings
        uiSettings.isLocationButtonEnabled = false
        uiSettings.isCompassEnabled = false
        uiSettings.isScaleBarEnabled = false
        uiSettings.isZoomControlEnabled = false
        uiSettings.isScrollGesturesEnabled = false
        uiSettings.isZoomGesturesEnabled = false
        uiSettings.isTiltGesturesEnabled = false
        uiSettings.isRotateGesturesEnabled = false

        // [수정] 지도가 준비된 후에 위치 권한을 확인하고 위치를 가져오도록 순서 변경
        checkPermissionAndGetLocation()
    }

    private fun checkPermissionAndGetLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation()
        } else {
            locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        progressBar.visibility = View.VISIBLE
        locationTextView.text = "현재 위치를 찾는 중..."

        val locationRequest = LocationRequest.create().apply {
            priority = Priority.PRIORITY_HIGH_ACCURACY
            numUpdates = 1
        }

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let {
                    updateUIToShowLocation(it)
                } ?: handleFailure("위치를 찾을 수 없습니다.")
                fusedLocationClient.removeLocationUpdates(this)
            }
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun updateUIToShowLocation(location: Location) {
        this.currentLocation = location

        if (::naverMap.isInitialized) {
            val currentPosition = LatLng(location.latitude, location.longitude)
            naverMap.moveCamera(CameraUpdate.scrollTo(currentPosition))
            Marker().apply {
                position = currentPosition
                map = naverMap
            }
        }

        val geocoder = Geocoder(this, Locale.KOREA)
        try {
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            if (addresses.isNullOrEmpty()) {
                handleFailure("주소 정보를 찾을 수 없습니다."); return
            }
            val address = addresses[0]
            val sido = address.adminArea
            val sigungu = address.locality ?: address.subLocality
            val dong = address.thoroughfare ?: address.subLocality

            if (sido != null && sigungu != null && dong != null) {
                currentLocationString = "$sido $sigungu $dong"
                val displayText = "$sigungu $dong"

                locationTextView.text = "현재 위치는\n'$displayText' 입니다."
                confirmButton.isEnabled = true
            } else {
                handleFailure("정확한 동네 정보를 찾을 수 없습니다.")
            }

        } catch (e: Exception) {
            handleFailure("주소 변환 중 오류가 발생했습니다.")
        } finally {
            progressBar.visibility = View.GONE
        }
    }

    private fun saveLocationToFirestore() {
        val user = Firebase.auth.currentUser
        if (user == null || currentLocationString == null || currentLocation == null) {
            Toast.makeText(this, "오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        confirmButton.isEnabled = false
        progressBar.visibility = View.VISIBLE

        val locationData = mapOf(
            "location" to currentLocationString,
            "locationPoint" to GeoPoint(currentLocation!!.latitude, currentLocation!!.longitude)
        )

        db.collection("users").document(user.uid)
            .update(locationData)
            .addOnSuccessListener {
                Toast.makeText(this, "'$currentLocationString' 인증 완료!", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "DB 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                confirmButton.isEnabled = true
                progressBar.visibility = View.GONE
            }
    }

    private fun handleFailure(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        locationTextView.text = message
        progressBar.visibility = View.GONE
        confirmButton.isEnabled = false
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