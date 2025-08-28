package com.bergi.nbang_v1.data.repository

import android.net.Uri
import com.bergi.nbang_v1.data.Post
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface PostRepository {
    suspend fun uploadPhoto(inputStream: InputStream): Result<Uri>
    suspend fun createPost(post: Post): Result<String>
}

@Singleton
class PostRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) : PostRepository {

    override suspend fun uploadPhoto(inputStream: InputStream): Result<Uri> {
        return try {
            val photoRef = storage.reference.child("images/${UUID.randomUUID()}.jpg")
            photoRef.putStream(inputStream).await()
            val downloadUrl = photoRef.downloadUrl.await()
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createPost(post: Post): Result<String> {
        return try {
            val newPostRef = firestore.collection("posts").document()
            val newPost = post.copy(id = newPostRef.id)
            newPostRef.set(newPost).await()
            Result.success(newPostRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
