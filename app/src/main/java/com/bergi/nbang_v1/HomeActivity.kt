package com.bergi.nbang_v1

import android.content.Intent
import android.os.Bundle
import android.util.Log // Log import 추가
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class HomeActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var textViewUserLocation: TextView
    private lateinit var recyclerViewPosts: RecyclerView
    private lateinit var fabCreatePost: FloatingActionButton

    private val tag = "HomeActivity_DEBUG" // 로그 태그

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(tag, "onCreate: Activity started.")

        auth = Firebase.auth
        Log.d(tag, "onCreate: Firebase auth initialized.")

        if (auth.currentUser == null) {
            Log.d(tag, "onCreate: User not logged in. Navigating to LoginActivity.")
            navigateToLogin()
            Log.d(tag, "onCreate: HomeActivity finished, LoginActivity should be starting.")
            return
        }

        Log.d(tag, "onCreate: User is logged in (${auth.currentUser?.email}). Proceeding to set content view.")

        try {
            Log.d(tag, "onCreate: Calling setContentView(R.layout.activity_home)")
            setContentView(R.layout.activity_home)
            Log.d(tag, "onCreate: setContentView_SUCCESSFUL")

            initViews()
            Log.d(tag, "onCreate: initViews() called successfully.")

            setupUI()
            Log.d(tag, "onCreate: setupUI() called successfully.")

        } catch (e: Exception) {
            Log.e(tag, "onCreate: CRITICAL_ERROR during UI setup or view initialization.", e)
        }
    }

    private fun initViews() {
        Log.d(tag, "initViews: Starting view initialization.")
        textViewUserLocation = findViewById(R.id.textViewUserLocation)
        Log.d(tag, "initViews: findViewById for textViewUserLocation. Found: ${if (this::textViewUserLocation.isInitialized) textViewUserLocation != null else false}")

        recyclerViewPosts = findViewById(R.id.recyclerViewPosts)
        Log.d(tag, "initViews: findViewById for recyclerViewPosts. Found: ${if (this::recyclerViewPosts.isInitialized) recyclerViewPosts != null else false}")

        fabCreatePost = findViewById(R.id.fabCreatePost)
        Log.d(tag, "initViews: findViewById for fabCreatePost. Found: ${if (this::fabCreatePost.isInitialized) fabCreatePost != null else false}")
        Log.d(tag, "initViews: View initialization finished.")
    }

    private fun setupUI() {
        Log.d(tag, "setupUI: Starting UI setup.")
        val currentUser = auth.currentUser
        val displayName = currentUser?.displayName
        val email = currentUser?.email
        Log.d(tag, "setupUI: Current user - DisplayName: $displayName, Email: $email")

        val welcomeMessage = when {
            !displayName.isNullOrEmpty() -> getString(R.string.welcome_user_nickname, displayName)
            !email.isNullOrEmpty() -> getString(R.string.welcome_user_email, email)
            else -> getString(R.string.welcome_default)
        }

        if (this::textViewUserLocation.isInitialized && textViewUserLocation != null) {
            textViewUserLocation.text = welcomeMessage
            Log.d(tag, "setupUI: Welcome message set to: $welcomeMessage")
        } else {
            Log.e(tag, "setupUI: textViewUserLocation is null or not initialized. Cannot set welcome text.")
        }

        if (this::recyclerViewPosts.isInitialized && recyclerViewPosts != null) {
            recyclerViewPosts.layoutManager = LinearLayoutManager(this)
            Log.d(tag, "setupUI: RecyclerView LayoutManager set.")

            val samplePosts = listOf(
                Post("첫 번째 게시글", "작성자 A"),
                Post("두 번째 이야기", "사용자 B"),
                Post("세 번째 공지사항", "관리자")
            )
            val postAdapter = PostAdapter(samplePosts)
            recyclerViewPosts.adapter = postAdapter
            Log.d(tag, "setupUI: RecyclerView Adapter set with sample posts.")
        } else {
            Log.e(tag, "setupUI: recyclerViewPosts is null or not initialized. Cannot set LayoutManager or Adapter.")
        }

        if (this::fabCreatePost.isInitialized && fabCreatePost != null) {
            fabCreatePost.setOnClickListener {
                Log.d(tag, "fabCreatePost clicked.")
                // TODO: 새 글 작성 화면으로 이동하는 로직 구현
            }
            Log.d(tag, "setupUI: fabCreatePost OnClickListener set.")
        } else {
            Log.e(tag, "setupUI: fabCreatePost is null or not initialized. Cannot set OnClickListener.")
        }
        Log.d(tag, "setupUI: UI setup finished.")
    }


    private fun navigateToLogin() {
        Log.d(tag, "navigateToLogin: called")
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_home, menu)
        Log.d(tag, "onCreateOptionsMenu: Menu inflated.")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d(tag, "onOptionsItemSelected: Item selected: ${item.title}")
        return when (item.itemId) {
            R.id.action_logout -> {
                Log.d(tag, "onOptionsItemSelected: Logout action selected.")
                signOut()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun signOut() {
        Log.d(tag, "signOut: Attempting to sign out.")
        auth.signOut()
        Log.d(tag, "signOut: Firebase auth.signOut() called.")
        navigateToLogin()
    }
}
