package com.bergi.nbang_v1

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.slider.Slider // Slider import 확인
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class KeywordSettingsActivity : AppCompatActivity() {

    private lateinit var editTextKeyword: EditText
    private lateinit var buttonAddKeyword: Button
    private lateinit var recyclerViewKeywords: RecyclerView
    private lateinit var textViewNoKeywords: TextView
    private lateinit var keywordAdapter: KeywordAdapter
    private val keywordsList = mutableListOf<String>()

    // 알림 반경 UI 요소 추가
    private lateinit var sliderNotificationRadius: Slider
    private lateinit var textViewRadiusValue: TextView

    private val firestore = Firebase.firestore
    private val currentUser = Firebase.auth.currentUser
    private val TAG = "KeywordSettings_DEBUG"

    private var currentNotificationRadiusKm: Double = 5.0 // 기본값

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keyword_settings)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            // onBackPressed() // Deprecated
            onBackPressedDispatcher.onBackPressed() // API 33+ 권장
        }

        editTextKeyword = findViewById(R.id.editTextKeyword)
        buttonAddKeyword = findViewById(R.id.buttonAddKeyword)
        recyclerViewKeywords = findViewById(R.id.recyclerViewKeywords)
        textViewNoKeywords = findViewById(R.id.textViewNoKeywords)

        // 알림 반경 UI 요소 초기화
        sliderNotificationRadius = findViewById(R.id.sliderNotificationRadius)
        textViewRadiusValue = findViewById(R.id.textViewRadiusValue)

        setupRecyclerView()
        loadKeywords()
        setupRadiusSlider() // 슬라이더 설정 호출
        loadNotificationRadius() // 저장된 반경 값 로드

        buttonAddKeyword.setOnClickListener {
            val keyword = editTextKeyword.text.toString().trim()
            if (keyword.isNotEmpty()) {
                addKeyword(keyword)
            } else {
                Toast.makeText(this, "키워드를 입력해주세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 뒤로가기 애니메이션을 위해 onBackPressed 재정의 (API 33 미만)
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }


    private fun setupRecyclerView() {
        keywordAdapter = KeywordAdapter(keywordsList) { keywordToDelete ->
            deleteKeyword(keywordToDelete)
        }
        recyclerViewKeywords.layoutManager = LinearLayoutManager(this)
        recyclerViewKeywords.adapter = keywordAdapter
    }

    private fun setupRadiusSlider() {
        sliderNotificationRadius.addOnChangeListener { slider, value, fromUser ->
            currentNotificationRadiusKm = value.toDouble()
            textViewRadiusValue.text = "${value.toInt()} km"
        }

        sliderNotificationRadius.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                // Do nothing
            }

            override fun onStopTrackingTouch(slider: Slider) {
                saveNotificationRadiusToFirestore(currentNotificationRadiusKm)
            }
        })
    }

    private fun loadNotificationRadius() {
        if (currentUser == null) {
            // 로그인하지 않은 사용자의 경우 기본값으로 UI 설정
            sliderNotificationRadius.value = currentNotificationRadiusKm.toFloat()
            textViewRadiusValue.text = "${currentNotificationRadiusKm.toInt()} km"
            Log.d(TAG, "로그인되지 않음. 알림 반경 기본값 ${currentNotificationRadiusKm}km 사용.")
            return
        }

        firestore.collection("users").document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val radius = document.getDouble("notificationRadiusKm")
                    if (radius != null) {
                        currentNotificationRadiusKm = radius
                        sliderNotificationRadius.value = radius.toFloat()
                        textViewRadiusValue.text = "${radius.toInt()} km"
                        Log.d(TAG, "저장된 알림 반경 로드: ${radius}km")
                    } else {
                        // 저장된 값이 없으면 기본값(5km) 사용 및 Firestore에 저장
                        sliderNotificationRadius.value = currentNotificationRadiusKm.toFloat()
                        textViewRadiusValue.text = "${currentNotificationRadiusKm.toInt()} km"
                        saveNotificationRadiusToFirestore(currentNotificationRadiusKm) // Firestore에 저장
                        Log.d(TAG, "저장된 알림 반경 없음. 기본값 ${currentNotificationRadiusKm}km 설정 및 저장.")
                    }
                } else {
                     // 사용자 문서가 아직 없을 수도 있음. 기본값 사용 및 Firestore에 저장 (문서 생성 포함)
                    sliderNotificationRadius.value = currentNotificationRadiusKm.toFloat()
                    textViewRadiusValue.text = "${currentNotificationRadiusKm.toInt()} km"
                    saveNotificationRadiusToFirestore(currentNotificationRadiusKm) // Firestore에 저장
                    Log.d(TAG, "사용자 문서 없음. 기본값 ${currentNotificationRadiusKm}km 설정 및 저장.")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "알림 반경 로드 실패", e)
                // 실패 시 기본값으로 설정
                sliderNotificationRadius.value = currentNotificationRadiusKm.toFloat()
                textViewRadiusValue.text = "${currentNotificationRadiusKm.toInt()} km"
            }
    }

    private fun saveNotificationRadiusToFirestore(radiusInKm: Double) {
        if (currentUser == null) {
            Toast.makeText(this, "알림 반경을 저장하려면 로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val userDocRef = firestore.collection("users").document(currentUser.uid)
        // merge 옵션을 사용하여 문서가 없으면 생성하고, 필드가 없으면 추가하며, 있으면 업데이트합니다.
        userDocRef.set(mapOf("notificationRadiusKm" to radiusInKm), SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "알림 반경 저장 성공: ${radiusInKm}km")
                Toast.makeText(this, "알림 반경 ${radiusInKm.toInt()}km 저장됨", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "알림 반경 저장 실패", e)
                Toast.makeText(this, "알림 반경 저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
    }


    private fun loadKeywords() {
        if (currentUser == null) {
            // 로그인하지 않은 사용자 처리 (키워드는 비워둠)
            keywordsList.clear()
            keywordAdapter.notifyDataSetChanged()
            updateKeywordsVisibility()
            Log.d(TAG, "로그인되지 않음. 키워드 목록 비움.")
            return
        }
        firestore.collection("users").document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                keywordsList.clear() // 기존 목록 초기화
                if (document != null && document.exists()) {
                    val storedKeywords = document.get("notificationKeywords") as? List<String>
                    if (storedKeywords != null) {
                        keywordsList.addAll(storedKeywords)
                    }
                } // 사용자 문서가 없어도 키워드는 없을 수 있으므로 여기서 특별한 처리는 안 함
                keywordAdapter.notifyDataSetChanged()
                updateKeywordsVisibility()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "키워드 로드 실패", e)
                Toast.makeText(this, "키워드를 불러오는데 실패했습니다.", Toast.LENGTH_SHORT).show()
                keywordsList.clear() // 실패 시 목록 비움
                keywordAdapter.notifyDataSetChanged()
                updateKeywordsVisibility()
            }
    }

    private fun addKeyword(keyword: String) {
        if (currentUser == null) {
             Toast.makeText(this, "키워드를 추가하려면 로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }
        if (keywordsList.contains(keyword)) {
            Toast.makeText(this, "이미 등록된 키워드입니다.", Toast.LENGTH_SHORT).show()
            return
        }
        // merge 옵션을 사용하여 문서나 필드가 없으면 생성
        firestore.collection("users").document(currentUser.uid)
            .set(mapOf("notificationKeywords" to FieldValue.arrayUnion(keyword)), SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "키워드 추가 성공: $keyword")
                // 로컬 리스트에도 추가 후 UI 업데이트 (Firestore에서 다시 읽어오는 대신)
                if (!keywordsList.contains(keyword)) {
                    keywordsList.add(keyword)
                    keywordAdapter.notifyItemInserted(keywordsList.size - 1)
                }
                editTextKeyword.text.clear()
                updateKeywordsVisibility()
                Toast.makeText(this, "'${keyword}' 추가됨", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "키워드 추가 실패", e)
                Toast.makeText(this, "키워드 추가에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteKeyword(keyword: String) {
        if (currentUser == null) {
            Toast.makeText(this, "키워드를 삭제하려면 로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }
        firestore.collection("users").document(currentUser.uid)
            .update("notificationKeywords", FieldValue.arrayRemove(keyword)) // arrayRemove는 필드가 있어야 함
            .addOnSuccessListener {
                Log.d(TAG, "키워드 삭제 성공: $keyword")
                val index = keywordsList.indexOf(keyword)
                if (index != -1) {
                    keywordsList.removeAt(index)
                    keywordAdapter.notifyItemRemoved(index)
                    keywordAdapter.notifyItemRangeChanged(index, keywordsList.size) // 아이템 삭제 후 인덱스 변경 반영
                }
                updateKeywordsVisibility()
                Toast.makeText(this, "'${keyword}' 삭제됨", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "키워드 삭제 실패. 해당 키워드가 없거나 다른 문제일 수 있습니다.", e)
                // 만약 필드 자체가 없을 경우를 대비해, 로컬 리스트에서만이라도 제거 시도
                val index = keywordsList.indexOf(keyword)
                if (index != -1) {
                    keywordsList.removeAt(index)
                    keywordAdapter.notifyItemRemoved(index)
                    keywordAdapter.notifyItemRangeChanged(index, keywordsList.size)
                    updateKeywordsVisibility()
                }
                Toast.makeText(this, "키워드 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateKeywordsVisibility() {
        if (keywordsList.isEmpty()) {
            recyclerViewKeywords.visibility = View.GONE
            textViewNoKeywords.visibility = View.VISIBLE
        } else {
            recyclerViewKeywords.visibility = View.VISIBLE
            textViewNoKeywords.visibility = View.GONE
        }
    }
}
