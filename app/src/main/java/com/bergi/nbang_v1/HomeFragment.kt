package com.bergi.nbang_v1

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.slider.Slider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.ktx.Firebase

class HomeFragment : Fragment() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var postAdapter: PostAdapter

    // UI 요소 변수
    private lateinit var userLocationTextView: TextView
    private lateinit var sliderGroup: ConstraintLayout
    private lateinit var distanceSlider: Slider
    private lateinit var distanceValueTextView: TextView
    private lateinit var fabCreatePost: FloatingActionButton // 변수 선언 위치 수정

    // 위치 및 반경 데이터
    private var currentUserLocation: GeoPoint? = null
    private var currentRadiusInKm: Double = 5.0 // 기본 반경 5km

    private val TAG = "HomeFragment_DEBUG"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        firestore = FirebaseFirestore.getInstance()

        // UI 요소 초기화
        val recyclerViewPosts = view.findViewById<RecyclerView>(R.id.recyclerViewPosts)
        fabCreatePost = view.findViewById(R.id.fabCreatePost) // 초기화 방식 수정
        userLocationTextView = view.findViewById(R.id.textViewUserLocation)
        sliderGroup = view.findViewById(R.id.sliderGroup)
        distanceSlider = view.findViewById(R.id.distanceSlider)
        distanceValueTextView = view.findViewById(R.id.textViewDistanceValue)

        setupRecyclerView(recyclerViewPosts)
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        loadUserLocationAndThenPosts()
    }

    private fun setupRecyclerView(recyclerView: RecyclerView) {
        postAdapter = PostAdapter(mutableListOf()) { post ->
            val intent = Intent(requireContext(), PostDetailActivity::class.java)
            intent.putExtra("POST_ID", post.id)
            startActivity(intent)
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = postAdapter
    }

    private fun setupClickListeners() {
        // 동네 이름 클릭 시 슬라이더 보이기/숨기기
        userLocationTextView.setOnClickListener {
            sliderGroup.visibility = if (sliderGroup.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        // 슬라이더 값 변경 시 텍스트 업데이트
        distanceSlider.addOnChangeListener { _, value, _ ->
            currentRadiusInKm = value.toDouble()
            distanceValueTextView.text = "${value.toInt()} km"
        }

        // 슬라이더 조작이 끝났을 때만 게시글 새로고침
        distanceSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                currentUserLocation?.let {
                    fetchNearbyPosts(it, currentRadiusInKm * 1000.0)
                }
            }
        })

        fabCreatePost.setOnClickListener {
            startActivity(Intent(requireContext(), CreatePostActivity::class.java))
        }
    }

    private fun loadUserLocationAndThenPosts() {
        val user = Firebase.auth.currentUser ?: return

        firestore.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val locationName = document.getString("location")
                    currentUserLocation = document.getGeoPoint("locationPoint")

                    // 주소에서 '동' 이름만 추출 (예: "대구광역시 북구 태전동" -> "태전동")
                    val dongName = locationName?.split(" ")?.lastOrNull()

                    if (!dongName.isNullOrEmpty() && currentUserLocation != null) {
                        userLocationTextView.text = dongName
                        // 인증된 위치가 있으면, 현재 슬라이더 값 기준으로 주변 게시글 검색
                        fetchNearbyPosts(currentUserLocation!!, currentRadiusInKm * 1000.0)
                    } else {
                        userLocationTextView.text = "MY 탭에서 동네를 인증해주세요."
                        postAdapter.updatePosts(emptyList())
                    }
                } else {
                    userLocationTextView.text = "MY 탭에서 동네를 인증해주세요."
                    postAdapter.updatePosts(emptyList())
                }
            }
    }

    private fun fetchNearbyPosts(centerPoint: GeoPoint, radiusInM: Double) {
        val center = GeoLocation(centerPoint.latitude, centerPoint.longitude)
        val bounds = GeoFireUtils.getGeoHashQueryBounds(center, radiusInM)
        val tasks = mutableListOf<Task<QuerySnapshot>>()

        for (b in bounds) {
            val q = firestore.collection("posts")
                .orderBy("geohash")
                .startAt(b.startHash)
                .endAt(b.endHash)
            tasks.add(q.get())
        }

        Tasks.whenAllComplete(tasks)
            .addOnSuccessListener {
                val matchingPosts = mutableListOf<Post>()
                for (task in tasks) {
                    val snap = task.result
                    for (doc in snap.documents) {
                        val geoPoint = doc.getGeoPoint("meetingLocation")
                        if (geoPoint != null) {
                            val docLocation = GeoLocation(geoPoint.latitude, geoPoint.longitude)
                            val distanceInM = GeoFireUtils.getDistanceBetween(docLocation, center)
                            if (distanceInM <= radiusInM) {
                                val post = doc.toObject(Post::class.java)
                                if (post != null) {
                                    post.id = doc.id
                                    matchingPosts.add(post)
                                }
                            }
                        }
                    }
                }
                matchingPosts.sortByDescending { it.timestamp }
                postAdapter.updatePosts(matchingPosts)
                Log.d(TAG, "반경 ${radiusInM/1000}km 내 게시글 ${matchingPosts.size}개를 찾았습니다.")
            }
            .addOnFailureListener {
                Log.e(TAG, "주변 게시글 검색 실패", it)
            }
    }
}
