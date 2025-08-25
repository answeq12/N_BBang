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
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.slider.Slider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
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
    private lateinit var fabCreatePost: FloatingActionButton
    private lateinit var chipGroupStatus: ChipGroup
    private lateinit var chipGroupCategory: ChipGroup

    // 위치 및 필터 데이터
    private var currentUserLocation: GeoPoint? = null
    private var currentRadiusInKm: Double = 5.0
    private var currentFilterStatus: String = "all"
    private var currentFilterCategory: String = "all"

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
        fabCreatePost = view.findViewById<FloatingActionButton>(R.id.fabCreatePost)
        userLocationTextView = view.findViewById<TextView>(R.id.textViewUserLocation)
        sliderGroup = view.findViewById<ConstraintLayout>(R.id.sliderGroup)
        distanceSlider = view.findViewById<Slider>(R.id.distanceSlider)
        distanceValueTextView = view.findViewById<TextView>(R.id.textViewDistanceValue)
        chipGroupStatus = view.findViewById<ChipGroup>(R.id.chipGroupStatus)
        chipGroupCategory = view.findViewById<ChipGroup>(R.id.chipGroupCategory)

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
                loadUserLocationAndThenPosts()
            }
        })

        // [수정] 필터 칩 클릭 리스너
        val refreshPostsListener: (ChipGroup, List<Int>) -> Unit = { chipGroup, checkedIds ->
            // 현재 선택된 칩 ID를 기반으로 필터 상태 변수 업데이트
            currentFilterStatus = when (chipGroupStatus.checkedChipId) {
                R.id.chipStatusRecruiting -> "모집중"
                R.id.chipStatusCompleted -> "모집완료" // 새로 추가
                else -> "all" // 기본값은 chipStatusAll 또는 정의되지 않은 ID
            }
            currentFilterCategory = when (chipGroupCategory.checkedChipId) {
                R.id.chipCategoryFood -> "음식 배달"
                R.id.chipCategoryPurchase -> "생필품 공동구매"
                R.id.chipCategoryErrandWant -> "대리구매 원해요" // 새로 추가
                R.id.chipCategoryErrandDo -> "대리구매 해드려요" // 새로 추가
                R.id.chipCategoryTaxi -> "택시 합승"       // 새로 추가
                R.id.chipCategoryEtc -> "기타"           // 새로 추가
                else -> "all" // 기본값은 chipCategoryAll 또는 정의되지 않은 ID
            }
            // 로그 추가: 현재 선택된 필터 상태와 카테고리 확인
            Log.d(TAG, "Filter changed - Status: $currentFilterStatus, Category: $currentFilterCategory")

            // 필터 상태가 변경되었으므로 게시글 목록을 새로고침
            loadUserLocationAndThenPosts()
        }

        chipGroupStatus.setOnCheckedStateChangeListener(refreshPostsListener)
        chipGroupCategory.setOnCheckedStateChangeListener(refreshPostsListener)

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

                    val dongName = locationName?.split(" ")?.lastOrNull()

                    if (!dongName.isNullOrEmpty() && currentUserLocation != null) {
                        userLocationTextView.text = dongName
                        fetchNearbyPosts(currentUserLocation!!, currentRadiusInKm * 1000.0)
                    } else {
                        userLocationTextView.text = "MY 탭에서 동네를 인증해주세요."
                        postAdapter.updatePosts(emptyList()) // 인증 안됐을 시 목록 비우기
                    }
                } else {
                    userLocationTextView.text = "MY 탭에서 동네를 인증해주세요."
                    postAdapter.updatePosts(emptyList()) // 사용자 정보 없을 시 목록 비우기
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "사용자 위치 정보 로드 실패", it)
                userLocationTextView.text = "위치 정보 로드 오류"
                postAdapter.updatePosts(emptyList()) // 오류 시 목록 비우기
            }
    }

    private fun fetchNearbyPosts(centerPoint: GeoPoint, radiusInM: Double) {
        val center = GeoLocation(centerPoint.latitude, centerPoint.longitude)
        val bounds = GeoFireUtils.getGeoHashQueryBounds(center, radiusInM)
        val tasks = mutableListOf<Task<QuerySnapshot>>()

        for (b in bounds) {
            var baseQuery: Query = firestore.collection("posts")

            // 1. 모집 상태 필터 적용
            if (currentFilterStatus != "all") {
                baseQuery = baseQuery.whereEqualTo("status", currentFilterStatus)
            }
            // 2. 카테고리 필터 적용
            if (currentFilterCategory != "all") {
                Log.d(TAG, "Firestore Query: Applying category filter - '$currentFilterCategory'")
                baseQuery = baseQuery.whereEqualTo("category", currentFilterCategory)
            }

            // 3. 위치 기반 쿼리 적용
            val q = baseQuery
                .orderBy("geohash")
                .startAt(b.startHash)
                .endAt(b.endHash)

            tasks.add(q.get())
        }

        Tasks.whenAllComplete(tasks)
            .addOnSuccessListener { taskResults ->
                val matchingPosts = mutableListOf<Post>()
                for (task in taskResults) {
                    if (task.isSuccessful) {
                        val snap = task.result as QuerySnapshot
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
                    } else {
                        Log.w(TAG, "하나 이상의 쿼리 실패", task.exception)
                    }
                }
                matchingPosts.sortByDescending { it.timestamp }
                postAdapter.updatePosts(matchingPosts)
                Log.d(TAG, "필터(상태:$currentFilterStatus, 카테고리:$currentFilterCategory), 반경 ${radiusInM/1000}km 내 게시글 ${matchingPosts.size}개를 찾았습니다.")
            }
            .addOnFailureListener {
                Log.e(TAG, "주변 게시글 검색 실패 (전체 작업)", it)
                postAdapter.updatePosts(emptyList()) // 전체 작업 실패 시 목록 비우기
            }
    }
}
