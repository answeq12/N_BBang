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
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import java.util.Locale

class VerifyLocationActivity : BaseActivity() {

    private lateinit var locationTextView: TextView
    private lateinit var confirmButton: Button
    private lateinit var progressBar: ProgressBar

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var db: FirebaseFirestore
    private var currentLocationString: String? = null
    private val TAG = "VerifyLocationActivity"

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            getCurrentLocation()
        } else {
            Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify_location)

        locationTextView = findViewById(R.id.textViewCurrentLocation)
        confirmButton = findViewById(R.id.buttonConfirmLocation)
        progressBar = findViewById(R.id.progressBar)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        db = FirebaseFirestore.getInstance()

        checkPermissionAndGetLocation()

        confirmButton.setOnClickListener {
            saveLocationToFirestore()
        }
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
            numUpdates = 1 // 한 번만 위치를 받아옴
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
        val geocoder = Geocoder(this, Locale.KOREA)
        try {
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            if (addresses.isNullOrEmpty()) {
                handleFailure("주소 정보를 찾을 수 없습니다."); return
            }
            val address = addresses[0]
            val sido = address.adminArea      // 예: "대구광역시"
            val sigungu = address.locality ?: address.subLocality // 예: "북구"
            val dong = address.thoroughfare ?: address.subLocality  // 예: "태전동"

            if (sido != null && sigungu != null && dong != null) {
                // Firestore에 저장할 전체 주소
                currentLocationString = "$sido $sigungu $dong"
                // 화면에 표시할 텍스트 (시/군/구 + 동)
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
        if (user == null || currentLocationString == null) {
            Toast.makeText(this, "오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        confirmButton.isEnabled = false
        progressBar.visibility = View.VISIBLE

        db.collection("users").document(user.uid)
            .update("location", currentLocationString)
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
}
