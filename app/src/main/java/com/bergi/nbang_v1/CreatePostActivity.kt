package com.bergi.nbang_v1

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import androidx.activity.result.contract.ActivityResultContracts
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.firebase.firestore.GeoPoint
import java.util.UUID

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class CreatePostActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    private lateinit var spinnerCategory: Spinner
    private lateinit var editTextTitle: EditText
    private lateinit var editTextContent: EditText
    private lateinit var editTextPeople: EditText
    private lateinit var buttonSelectPlace: Button
    private lateinit var textViewSelectedPlace: TextView
    private lateinit var createButton: Button
    private lateinit var selectPhotoButton: Button

    private val TAG = "CreatePostActivity"
    private val selectedPhotos = mutableListOf<Uri>()
    private var selectedLatitude: Double = 0.0
    private var selectedLongitude: Double = 0.0

    private val naverApiService: NaverApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://naveropenapi.apigw.ntruss.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(NaverApiService::class.java)
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                launchImagePicker()
            } else {
                Toast.makeText(this, "갤러리 접근 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }

    private val pickImagesLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                selectedPhotos.clear()
                result.data?.clipData?.let { clipData ->
                    for (i in 0 until clipData.itemCount) {
                        val uri = clipData.getItemAt(i).uri
                        selectedPhotos.add(uri)
                    }
                } ?: result.data?.data?.let { uri ->
                    selectedPhotos.add(uri)
                }
                Toast.makeText(this, "${selectedPhotos.size}장의 사진이 선택되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }

    private val selectLocationLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val latitude = result.data?.getDoubleExtra("latitude", 0.0)
                val longitude = result.data?.getDoubleExtra("longitude", 0.0)
                if (latitude != null && longitude != null) {
                    selectedLatitude = latitude
                    selectedLongitude = longitude
                    getAddressFromCoordinates(longitude, latitude)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_post)

        auth = Firebase.auth
        firestore = FirebaseFirestore.getInstance()
        storage = Firebase.storage

        spinnerCategory = findViewById(R.id.spinnerCategory)
        editTextTitle = findViewById(R.id.editTextPostTitle)
        editTextContent = findViewById(R.id.editTextPostContent)
        editTextPeople = findViewById(R.id.editTextTotalPeople)
        buttonSelectPlace = findViewById(R.id.buttonSelectPlace)
        textViewSelectedPlace = findViewById(R.id.textViewSelectedPlace)
        createButton = findViewById(R.id.buttonCreatePost)
        selectPhotoButton = findViewById(R.id.selectPhotoButton)

        setupSpinner()

        selectPhotoButton.setOnClickListener {
            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        buttonSelectPlace.setOnClickListener {
            val intent = Intent(this, SelectLocationActivity::class.java)
            selectLocationLauncher.launch(intent)
        }

        createButton.setOnClickListener {
            if (selectedPhotos.isEmpty()) {
                createPost(emptyList())
            } else {
                uploadPhotos()
            }
        }
    }

    private fun getAddressFromCoordinates(longitude: Double, latitude: Double) {
        val coords = "$longitude,$latitude"
        val call = naverApiService.reverseGeocode("t0no2o6lq3", "Yf8MWlbYLudKmeEu8mjQSRF7wGTMCZAdAoQ3cAHq", coords)
        call.enqueue(object : Callback<ReverseGeocodingResponse> {
            override fun onResponse(
                call: Call<ReverseGeocodingResponse>,
                response: Response<ReverseGeocodingResponse>
            ) {
                if (response.isSuccessful) {
                    val address = response.body()?.results?.firstOrNull()
                    if (address != null) {
                        val addressName = "${address.region.area1.name} ${address.region.area2.name} ${address.land.name} ${address.land.number1}"
                        textViewSelectedPlace.text = addressName
                    } else {
                        textViewSelectedPlace.text = "주소를 찾을 수 없습니다."
                    }
                } else {
                    Log.e(TAG, "Reverse geocoding failed: ${response.errorBody()?.string()}")
                    textViewSelectedPlace.text = "주소 변환 실패"
                }
            }

            override fun onFailure(call: Call<ReverseGeocodingResponse>, t: Throwable) {
                Log.e(TAG, "Reverse geocoding failed", t)
                textViewSelectedPlace.text = "주소 변환 실패"
            }
        })
    }

    private fun setupSpinner() {
        val categories = listOf("음식 배달", "생필품 공동구매", "대리구매 원해요", "대리구매 해드려요", "택시 합승", "기타")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = adapter
    }

    private fun launchImagePicker() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        pickImagesLauncher.launch(intent)
    }

    private fun uploadPhotos() {
        createButton.isEnabled = false
        val uploadTasks = selectedPhotos.map { uri ->
            val photoRef = storage.reference.child("images/${UUID.randomUUID()}.jpg")
            val inputStream = contentResolver.openInputStream(uri)
            photoRef.putStream(inputStream!!).continueWithTask { task ->
                if (!task.isSuccessful) {
                    task.exception?.let { throw it }
                }
                photoRef.downloadUrl
            }
        }

        Tasks.whenAllSuccess<Uri>(uploadTasks)
            .addOnSuccessListener { downloadUrls ->
                val photoUrls = downloadUrls.map { it.toString() }
                createPost(photoUrls)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error uploading photos", e)
                Toast.makeText(this, "사진 업로드에 실패했습니다: ${e.message}", Toast.LENGTH_LONG).show()
                createButton.isEnabled = true
            }
    }

    private fun createPost(photoUrls: List<String>) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            createButton.isEnabled = true
            return
        }

        val creatorName = currentUser.displayName ?: "익명"
        val category = spinnerCategory.selectedItem.toString()
        val title = editTextTitle.text.toString().trim()
        val content = editTextContent.text.toString().trim()
        val peopleStr = editTextPeople.text.toString().trim()
        val placeName = textViewSelectedPlace.text.toString().trim()

        if (title.isEmpty() || content.isEmpty() || peopleStr.isEmpty() || placeName.isEmpty() || selectedLatitude == 0.0 || selectedLongitude == 0.0) {
            Toast.makeText(this, "모든 항목을 입력해주세요.", Toast.LENGTH_SHORT).show()
            createButton.isEnabled = true
            return
        }

        val totalPeople = peopleStr.toIntOrNull()
        if (totalPeople == null || totalPeople <= 1) {
            Toast.makeText(this, "인원(2명 이상)을 올바르게 입력해주세요.", Toast.LENGTH_SHORT).show()
            createButton.isEnabled = true
            return
        }

        // --- 이 부분을 수정했습니다 ---
        // 1. GeoPoint 객체 생성
        val meetingLocation = GeoPoint(selectedLatitude, selectedLongitude)
        // 2. GeoHash 값 계산
        val geohash = GeoFireUtils.getGeoHashForLocation(GeoLocation(selectedLatitude, selectedLongitude))

        val newPost = Post(
            title = title,
            content = content,
            category = category,
            creatorName = creatorName,
            photoUrls = photoUrls,
            totalPeople = totalPeople,
            meetingPlaceName = placeName,
            meetingLocation = meetingLocation,
            geohash = geohash, // GeoHash 값 저장
            creatorUid = currentUser.uid,
            participants = listOf(currentUser.uid)
        )
        // --- ---

        firestore.collection("posts")
            .add(newPost)
            .addOnSuccessListener { documentReference ->
                Log.d(TAG, "Post uploaded successfully: ${documentReference.id}")
                Toast.makeText(this, "게시글이 등록되었습니다.", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, PostDetailActivity::class.java)
                intent.putExtra("POST_ID", documentReference.id)
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error uploading post", e)
                Toast.makeText(this, "게시글 등록에 실패했습니다: ${e.message}", Toast.LENGTH_LONG).show()
                createButton.isEnabled = true
            }
    }
}
