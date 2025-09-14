// MapDetailActivity.kt
package com.bergi.nbang_v1

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.OnMapReadyCallback
import com.naver.maps.map.overlay.Marker

class MapDetailActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private var location: LatLng? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_detail)

        // Intent로부터 위도, 경도 값 받기
        val latitude = intent.getDoubleExtra("latitude", 0.0)
        val longitude = intent.getDoubleExtra("longitude", 0.0)
        if (latitude != 0.0 && longitude != 0.0) {
            location = LatLng(latitude, longitude)
        }

        mapView = findViewById(R.id.detailedMapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        findViewById<ImageButton>(R.id.buttonBack).setOnClickListener {
            finish() // 뒤로가기 버튼 클릭 시 현재 액티비티 종료
        }
    }

    override fun onMapReady(naverMap: NaverMap) {
        location?.let { loc ->
            // 카메라를 전달받은 위치로 이동
            naverMap.moveCamera(CameraUpdate.scrollAndZoomTo(loc, 16.0))
            // 마커 표시
            Marker().apply {
                position = loc
                map = naverMap
            }
        }
    }

    // MapView 생명주기 메서드
    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
}