package com.bergi.nbang_v1

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import java.util.Locale

class VerifyLocationActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var db: FirebaseFirestore

    private lateinit var statusTextView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var getLocationButton: Button

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            getCurrentLocationAndVerify()
        } else {
            Toast.makeText(this, "동네 인증을 위해 위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            statusTextView.text = "위치 권한이 거부되었습니다."
            progressBar.visibility = View.GONE
            getLocationButton.isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify_location)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        db = FirebaseFirestore.getInstance()

        statusTextView = findViewById(R.id.textViewStatus)
        progressBar = findViewById(R.id.progressBar)
        getLocationButton = findViewById(R.id.buttonGetLocation)

        getLocationButton.setOnClickListener {
            checkLocationPermissionAndGetLocation()
        }
    }

    private fun checkLocationPermissionAndGetLocation() {
        progressBar.visibility = View.VISIBLE
        getLocationButton.isEnabled = false
        statusTextView.text = "현재 위치를 확인하는 중..."

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocationAndVerify()
        } else {
            locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun getCurrentLocationAndVerify() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val geocoder = Geocoder(this, Locale.KOREA)
                try {
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    if (addresses?.isNotEmpty() == true) {
                        val neighborhood = addresses[0].subLocality // '동'
                        if (neighborhood != null) {
                            showConfirmationDialog(neighborhood)
                        } else {
                            handleFailure("현재 위치의 동 정보를 찾을 수 없습니다.")
                        }
                    } else {
                        handleFailure("주소를 찾을 수 없습니다.")
                    }
                } catch (e: Exception) {
                    handleFailure("주소 변환 중 오류 발생: ${e.message}")
                }
            } else {
                handleFailure("위치 정보를 가져올 수 없습니다. GPS를 켜고 잠시 후 다시 시도해주세요.")
            }
        }.addOnFailureListener {
            handleFailure("위치 정보 요청 실패: ${it.message}")
        }
    }

    private fun showConfirmationDialog(location: String) {
        AlertDialog.Builder(this)
            .setTitle("동네 인증 확인")
            .setMessage("현재 위치는 '$location'입니다. 이 위치로 인증하시겠습니까?")
            .setPositiveButton("인증하기") { _, _ ->
                updateLocationInFirestore(location)
            }
            .setNegativeButton("취소") { _, _ ->
                handleFailure("인증이 취소되었습니다.")
            }
            .setCancelable(false)
            .show()
    }

    private fun updateLocationInFirestore(location: String) {
        val user = Firebase.auth.currentUser
        if (user == null) {
            handleFailure("로그인 정보가 없습니다.")
            return
        }
        db.collection("users").document(user.uid)
            .update("location", location)
            .addOnSuccessListener {
                Toast.makeText(this, "'$location' 인증 완료!", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK) // 성공 결과를 ProfileFragment에 알림
                finish()
            }
            .addOnFailureListener { e ->
                handleFailure("DB 저장 실패: ${e.message}")
            }
    }

    private fun handleFailure(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        statusTextView.text = message
        progressBar.visibility = View.GONE
        getLocationButton.isEnabled = true
    }
}