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
import com.bergi.nbang_v1.PostAdapter
import com.bergi.nbang_v1.data.Post
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
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.firebase.Timestamp // Firebase Timestamp 클래스 임포트
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

class HomeFragment : Fragment() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var postAdapter: PostAdapter

    private lateinit var userLocationTextView: TextView
    private lateinit var sliderGroup: ConstraintLayout
    private lateinit var distanceSlider: Slider
    private lateinit var distanceValueTextView: TextView
    private lateinit var fabCreatePost: FloatingActionButton
    private lateinit var chipGroupStatus: ChipGroup
    private lateinit var chipGroupCategory: ChipGroup

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
        userLocationTextView.setOnClickListener {
            sliderGroup.visibility = if (sliderGroup.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        distanceSlider.addOnChangeListener { _, value, _ ->
            currentRadiusInKm = value.toDouble()
            distanceValueTextView.text = "${value.toInt()} km"
        }

        distanceSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                loadUserLocationAndThenPosts()
            }
        })

        val refreshPostsListener: (ChipGroup, List<Int>) -> Unit = { chipGroup, checkedIds ->
            currentFilterStatus = when (chipGroupStatus.checkedChipId) {
                R.id.chipStatusRecruiting -> "모집중"
                R.id.chipStatusCompleted -> "모집완료"
                else -> "all"
            }
            currentFilterCategory = when (chipGroupCategory.checkedChipId) {
                R.id.chipCategoryFood -> "음식 배달"
                R.id.chipCategoryPurchase -> "생필품 공동구매"
                R.id.chipCategoryErrandWant -> "대리구매 원해요"
                R.id.chipCategoryErrandDo -> "대리구매 해드려요"
                R.id.chipCategoryTaxi -> "택시 합승"
                R.id.chipCategoryEtc -> "기타"
                else -> "all"
            }
            Log.d(TAG, "Filter changed - Status: $currentFilterStatus, Category: $currentFilterCategory")
            loadUserLocationAndThenPosts()
        }

        chipGroupStatus.setOnCheckedStateChangeListener(refreshPostsListener)
        chipGroupCategory.setOnCheckedStateChangeListener(refreshPostsListener)

        fabCreatePost.setOnClickListener {
            startActivity(Intent(requireContext(), CreatePostActivity::class.java))
        }
    }

    private fun loadUserLocationAndThenPosts() {
        val user = Firebase.auth.currentUser ?: run {
            Log.w(TAG, "User not logged in. Cannot load posts.")
            postAdapter.updatePosts(emptyList())
            userLocationTextView.text = "로그인이 필요합니다."
            return
        }

        firestore.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val locationName = document.getString("location")
                    currentUserLocation = document.getGeoPoint("locationPoint")
                    val dongName = locationName?.split(" ")?.lastOrNull()

                    Log.d(TAG, "User location loaded: Name=$locationName, Point=$currentUserLocation")

                    if (!dongName.isNullOrEmpty() && currentUserLocation != null) {
                        userLocationTextView.text = dongName
                        fetchNearbyPosts(currentUserLocation!!, currentRadiusInKm * 1000.0)
                    } else {
                        Log.w(TAG, "User location (dongName or currentUserLocation) is invalid. Dong: $dongName, LocationPoint: $currentUserLocation")
                        userLocationTextView.text = "MY 탭에서 동네를 인증해주세요."
                        postAdapter.updatePosts(emptyList())
                    }
                } else {
                    Log.w(TAG, "User document does not exist or is null. UID: ${user.uid}")
                    userLocationTextView.text = "MY 탭에서 동네를 인증해주세요."
                    postAdapter.updatePosts(emptyList())
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to load user location", e)
                userLocationTextView.text = "위치 정보 로드 오류"
                postAdapter.updatePosts(emptyList())
            }
    }

    private fun fetchNearbyPosts(centerPoint: GeoPoint, radiusInM: Double) {
        val center = GeoLocation(centerPoint.latitude, centerPoint.longitude)
        val bounds = GeoFireUtils.getGeoHashQueryBounds(center, radiusInM)
        val tasks = mutableListOf<Task<QuerySnapshot>>()

        Log.d(TAG, "fetchNearbyPosts: Center=$centerPoint, RadiusM=$radiusInM, StatusFilter='$currentFilterStatus', CategoryFilter='$currentFilterCategory'")

        for (b in bounds) {
            var baseQuery: Query = firestore.collection("posts")

            if (currentFilterStatus != "all") {
                baseQuery = baseQuery.whereEqualTo("status", currentFilterStatus)
            }
            if (currentFilterCategory != "all") {
                baseQuery = baseQuery.whereEqualTo("category", currentFilterCategory)
            }

            val q = baseQuery
                .orderBy("geohash")
                .startAt(b.startHash)
                .endAt(b.endHash)
            tasks.add(q.get())
        }

        Tasks.whenAllComplete(tasks)
            .addOnSuccessListener { taskResults ->
                val matchingPosts = mutableListOf<Post>()
                var totalDocsProcessed = 0

                for ((index, task) in taskResults.withIndex()) {
                    if (task.isSuccessful) {
                        val snap = task.result as QuerySnapshot
                        totalDocsProcessed += snap.size()
                        Log.d(TAG, "GeoQuery batch #$index successful, ${snap.size()} documents found.")

                        for (doc in snap.documents) {
                            val postId = doc.id
                            val postStatus = doc.getString("status")
                            val postCategory = doc.getString("category")
                            val postTitle = doc.getString("title")
                            val postGeoPoint = doc.getGeoPoint("meetingLocation")

                            Log.d(TAG, "Processing Post ID: $postId, Title: '$postTitle', Status: '$postStatus', Category: '$postCategory', Location: $postGeoPoint")

                            var skipReason: String? = null

                            if (postGeoPoint == null) {
                                skipReason = "meetingLocation is null"
                            } else {
                                val docLocation = GeoLocation(postGeoPoint.latitude, postGeoPoint.longitude)
                                val distanceInM = GeoFireUtils.getDistanceBetween(docLocation, center)
                                Log.d(TAG, "Post ID: $postId, Distance: ${String.format("%.2f", distanceInM)}m (Radius: ${radiusInM}m)")

                                if (distanceInM > radiusInM) {
                                    skipReason = "outside radius (${String.format("%.2f", distanceInM)}m > ${radiusInM}m)"
                                }
                            }

                            if (skipReason != null) {
                                Log.d(TAG, "Post ID: $postId SKIPPED. Reason: $skipReason. Title: '$postTitle'")
                                continue
                            }

                            val post = doc.toObject(Post::class.java)
                            if (post != null) {
                                post.id = doc.id
                                matchingPosts.add(post)
                                Log.d(TAG, "Post ID: $postId ADDED to list. Title: '${post.title}'")
                            } else {
                                Log.w(TAG, "Post ID: $postId FAILED to convert to Post object. Firestore Data: ${doc.data}")
                            }
                        }
                    } else {
                        Log.w(TAG, "GeoQuery batch #$index FAILED.", task.exception)
                    }
                }
                Log.d(TAG, "Total documents processed across all GeoQuery batches: $totalDocsProcessed")

                matchingPosts.sortWith(compareByDescending { it.timestamp ?: Timestamp(0,0) })

                postAdapter.updatePosts(matchingPosts)
                Log.d(TAG, "UpdatePosts called. Filters(Status:$currentFilterStatus, Cat:$currentFilterCategory), Radius ${radiusInM/1000}km. Found ${matchingPosts.size} posts.")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to fetch nearby posts (overall task failure)", e)
                postAdapter.updatePosts(emptyList())
            }
    }
}