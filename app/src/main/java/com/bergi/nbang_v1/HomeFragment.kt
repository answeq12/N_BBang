package com.bergi.nbang_v1

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bergi.nbang_v1.data.Post
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.slider.Slider
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.ktx.Firebase
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation

class HomeFragment : Fragment() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var postAdapter: PostAdapter

    // UI 요소
    private lateinit var searchView: SearchView
    private lateinit var userLocationTextView: TextView
    private lateinit var sliderGroup: ConstraintLayout
    private lateinit var distanceSlider: Slider
    private lateinit var distanceValueTextView: TextView
    private lateinit var fabCreatePost: FloatingActionButton
    private lateinit var chipGroupStatus: ChipGroup
    private lateinit var chipGroupCategory: ChipGroup
    private lateinit var recyclerViewPosts: RecyclerView
    private lateinit var loadingOrEmptyLayout: FrameLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var textViewStatus: TextView

    // 데이터 및 상태
    private var currentUserLocation: GeoPoint? = null
    private var currentRadiusInKm: Double = 5.0
    private var currentFilterStatus: String = "all"
    private var currentFilterCategory: String = "all"
    private var currentSearchQuery: String? = null

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
        initViews(view)
        setupRecyclerView()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        loadUserLocationAndThenPosts()
    }

    private fun initViews(view: View) {
        recyclerViewPosts = view.findViewById(R.id.recyclerViewPosts)
        searchView = view.findViewById(R.id.searchView)
        fabCreatePost = view.findViewById(R.id.fabCreatePost)
        userLocationTextView = view.findViewById(R.id.textViewUserLocation)
        sliderGroup = view.findViewById(R.id.sliderGroup)
        distanceSlider = view.findViewById(R.id.distanceSlider)
        distanceValueTextView = view.findViewById(R.id.textViewDistanceValue)
        chipGroupStatus = view.findViewById(R.id.chipGroupStatus)
        chipGroupCategory = view.findViewById(R.id.chipGroupCategory)
        loadingOrEmptyLayout = view.findViewById(R.id.loadingOrEmptyLayout)
        progressBar = view.findViewById(R.id.progressBar)
        textViewStatus = view.findViewById(R.id.textViewStatus)
    }

    private fun setupRecyclerView() {
        postAdapter = PostAdapter(mutableListOf()) { post ->
            val intent = Intent(requireContext(), PostDetailActivity::class.java)
            intent.putExtra("POST_ID", post.id)
            startActivity(intent)
        }
        recyclerViewPosts.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewPosts.adapter = postAdapter
    }

    private fun setupClickListeners() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                currentSearchQuery = query?.trim()?.lowercase()
                loadUserLocationAndThenPosts()
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean = false
        })

        val closeButton = searchView.findViewById<View>(androidx.appcompat.R.id.search_close_btn)
        closeButton.setOnClickListener {
            currentSearchQuery = null
            searchView.setQuery("", false)
            searchView.clearFocus()
            loadUserLocationAndThenPosts()
        }

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

        val refreshPostsListener: (ChipGroup, List<Int>) -> Unit = { _ , _ ->
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

        showLoading(true, "게시글을 찾고 있습니다...")

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
                        userLocationTextView.text = "동네 인증 필요"
                        showEmptyResults("MY 탭에서 동네를 인증해주세요.")
                    }
                } else {
                    userLocationTextView.text = "동네 인증 필요"
                    showEmptyResults("MY 탭에서 동네를 인증해주세요.")
                }
            }
            .addOnFailureListener {
                showEmptyResults("사용자 정보를 불러오는 데 실패했습니다.")
            }
    }

    private fun fetchNearbyPosts(centerPoint: GeoPoint, radiusInM: Double) {
        val center = GeoLocation(centerPoint.latitude, centerPoint.longitude)
        val bounds = GeoFireUtils.getGeoHashQueryBounds(center, radiusInM)
        val tasks = mutableListOf<Task<QuerySnapshot>>()

        for (b in bounds) {
            var q: Query = firestore.collection("posts")

            if (currentFilterStatus != "all") {
                q = q.whereEqualTo("status", currentFilterStatus)
            }
            if (currentFilterCategory != "all") {
                q = q.whereEqualTo("category", currentFilterCategory)
            }

            tasks.add(q.orderBy("geohash").startAt(b.startHash).endAt(b.endHash).get())
        }

        Tasks.whenAllComplete(tasks)
            .addOnSuccessListener { taskResults ->
                var matchingPosts = mutableListOf<Post>()
                for (task in taskResults) {
                    if (task.isSuccessful) {
                        val snap = task.result as QuerySnapshot
                        for (doc in snap.documents) {
                            val postGeoPoint = doc.getGeoPoint("meetingLocation")
                            if (postGeoPoint != null) {
                                val docLocation = GeoLocation(postGeoPoint.latitude, postGeoPoint.longitude)
                                if (GeoFireUtils.getDistanceBetween(docLocation, center) <= radiusInM) {
                                    doc.toObject(Post::class.java)?.let {
                                        it.id = doc.id
                                        matchingPosts.add(it)
                                    }
                                }
                            }
                        }
                    }
                }

                if (!currentSearchQuery.isNullOrEmpty()) {
                    matchingPosts = matchingPosts.filter { post ->
                        post.keywords.any { keyword -> keyword.contains(currentSearchQuery!!, ignoreCase = true) } ||
                                post.title.contains(currentSearchQuery!!, ignoreCase = true)||
                                post.content.contains(currentSearchQuery!!, ignoreCase = true)
                    }.toMutableList()
                }

                matchingPosts.sortWith(compareByDescending { it.timestamp ?: Timestamp(0, 0) })

                if (matchingPosts.isEmpty()) {
                    showEmptyResults("검색 결과가 없습니다.")
                } else {
                    showLoading(false)
                    postAdapter.updatePosts(matchingPosts)
                }
            }
            .addOnFailureListener {
                showEmptyResults("게시글을 불러오는 데 실패했습니다.")
            }
    }

    private fun showLoading(isLoading: Boolean, message: String? = null) {
        if (isLoading) {
            loadingOrEmptyLayout.visibility = View.VISIBLE
            progressBar.visibility = View.VISIBLE
            textViewStatus.visibility = View.VISIBLE
            textViewStatus.text = message
            recyclerViewPosts.visibility = View.GONE
        } else {
            loadingOrEmptyLayout.visibility = View.GONE
            recyclerViewPosts.visibility = View.VISIBLE
        }
    }

    private fun showEmptyResults(message: String) {
        loadingOrEmptyLayout.visibility = View.VISIBLE
        progressBar.visibility = View.GONE
        textViewStatus.visibility = View.VISIBLE
        textViewStatus.text = message
        recyclerViewPosts.visibility = View.GONE
        postAdapter.updatePosts(emptyList()) // 어댑터의 데이터도 비워줍니다.
    }
}
