package com.bergi.nbang_v1

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import java.util.Locale

class VerifyLocationActivity : AppCompatActivity() {

    // UI 요소
    private lateinit var spinnerSido: Spinner
    private lateinit var spinnerSigungu: Spinner
    private lateinit var statusTextView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var verifyButton: Button

    // 위치 서비스 및 DB
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var db: FirebaseFirestore
    private val TAG = "VerifyLocation_DEBUG" // 로그 태그 변경

    private val daeguSigunguList: List<String> = listOf(
        "중구", "동구", "서구", "남구", "북구", "수성구", "달서구", "달성군", "군위군"
    )

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) verifySelectedLocation() else handleFailure("위치 권한이 거부되었습니다.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify_location)
        initViews()
        initServices()
        setupSpinners()
        setupListeners()
    }

    private fun initViews() {
        spinnerSido = findViewById(R.id.spinnerSido)
        spinnerSigungu = findViewById(R.id.spinnerSigungu)
        statusTextView = findViewById(R.id.textViewStatus)
        progressBar = findViewById(R.id.progressBar)
        verifyButton = findViewById(R.id.buttonVerifySelectedLocation)
    }

    private fun initServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        db = FirebaseFirestore.getInstance()
    }

    private fun setupSpinners() {
        val sidoAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf("대구광역시"))
        spinnerSido.adapter = sidoAdapter
        spinnerSido.isEnabled = false

        val sigunguList = listOf("구/군 선택") + daeguSigunguList
        val sigunguAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sigunguList)
        spinnerSigungu.adapter = sigunguAdapter
        sigunguAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }

    private fun setupListeners() {
        verifyButton.setOnClickListener {
            verifySelectedLocation()
        }
    }

    private fun verifySelectedLocation() {
        val selectedSigungu = spinnerSigungu.selectedItem.toString()

        if (selectedSigungu == "구/군 선택") {
            Toast.makeText(this, "인증할 지역(구/군)을 선택해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        verifyButton.isEnabled = false
        statusTextView.text = "현재 위치를 확인하여 '$selectedSigungu'과(와) 비교하는 중..."

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocationAndCompare(selectedSigungu)
        } else {
            locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun getCurrentLocationAndCompare(selectedSigungu: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                compareLocation(location, selectedSigungu)
            } else {
                val locationRequest = LocationRequest.create().apply {
                    priority = Priority.PRIORITY_HIGH_ACCURACY
                    interval = 5000
                    fastestInterval = 1000
                    numUpdates = 1
                }

                val locationCallback = object : LocationCallback() {
                    override fun onLocationResult(locationResult: LocationResult) {
                        val lastLocation = locationResult.lastLocation
                        if (lastLocation != null) {
                            compareLocation(lastLocation, selectedSigungu)
                        } else {
                            handleFailure("현재 위치를 가져오는 데 실패했습니다.")
                        }
                        fusedLocationClient.removeLocationUpdates(this)
                    }
                }
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            }
        }.addOnFailureListener {
            handleFailure("위치 정보 요청에 실패했습니다.")
        }
    }

    // --- 이 부분을 수정했습니다 ---
    private fun compareLocation(location: Location, selectedSigungu: String) {
        val geocoder = Geocoder(this, Locale.KOREA)
        Log.d(TAG, "주소 변환 시도. Lat: ${location.latitude}, Lon: ${location.longitude}")

        try {
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)

            if (addresses.isNullOrEmpty()) {
                Log.w(TAG, "주소 변환 실패: Geocoder가 주소를 반환하지 않았습니다.")
                handleFailure("주소 정보를 찾을 수 없습니다. 네트워크 연결을 확인해주세요.")
                return
            }

            val address = addresses[0]
            val currentSigungu = address.locality // 'locality'가 시/군/구에 해당합니다.
            Log.d(TAG, "주소 변환 성공: 전체 주소 - ${address.getAddressLine(0)}, locality - $currentSigungu")

            if (currentSigungu == selectedSigungu) {
                val fullLocation = "대구광역시 $selectedSigungu"
                updateLocationInFirestore(fullLocation)
            } else {
                handleFailure("인증 실패: 선택하신 '$selectedSigungu'이(가) 아닙니다.\n(현재 위치: ${currentSigungu ?: "알 수 없음"})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "주소 변환 중 예외 발생", e)
            handleFailure("주소 변환 중 오류가 발생했습니다.")
        }
    }

    private fun updateLocationInFirestore(location: String) {
        val user = Firebase.auth.currentUser ?: return
        db.collection("users").document(user.uid)
            .update("location", location)
            .addOnSuccessListener {
                Toast.makeText(this, "'$location' 인증 완료!", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK)
                finish()
            }
            .addOnFailureListener { e -> handleFailure("DB 저장 실패: ${e.message}") }
    }

    private fun handleFailure(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        statusTextView.text = message
        progressBar.visibility = View.GONE
        verifyButton.isEnabled = true
    }
}
