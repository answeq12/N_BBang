package com.bergi.nbang_v1.presentation.createpost

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.bergi.nbang_v1.data.Post
import com.bergi.nbang_v1.data.repository.PostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream
import javax.inject.Inject

@HiltViewModel
class CreatePostViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _eventFlow = MutableSharedFlow<CreatePostEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    fun uploadPhotosAndCreatePost(selectedPhotoUris: List<Uri>, getInputStream: (Uri) -> InputStream?) = viewModelScope.launch {
        _isLoading.value = true
        try {
            val photoUrls = mutableListOf<String>()
            for (uri in selectedPhotoUris) {
                val inputStream = getInputStream(uri)
                if (inputStream != null) {
                    postRepository.uploadPhoto(inputStream)
                        .onSuccess { downloadUrl ->
                            photoUrls.add(downloadUrl.toString())
                        }
                        .onFailure { e ->
                            _eventFlow.emit(CreatePostEvent.Error("사진 업로드 실패: ${e.message}"))
                            _isLoading.value = false
                            return@launch // Stop if any photo upload fails
                        }
                } else {
                    _eventFlow.emit(CreatePostEvent.Error("사진을 읽을 수 없습니다: $uri"))
                    _isLoading.value = false
                    return@launch // Stop if input stream is null
                }
            }
            _eventFlow.emit(CreatePostEvent.PhotosUploaded(photoUrls))
        } catch (e: Exception) {
            _eventFlow.emit(CreatePostEvent.Error("사진 업로드 중 예상치 못한 오류: ${e.message}"))
        } finally {
            _isLoading.value = false
        }
    }

    fun createPost(post: Post) = viewModelScope.launch {
        _isLoading.value = true
        try {
            postRepository.createPost(post)
                .onSuccess {
                    _eventFlow.emit(CreatePostEvent.PostCreated)
                }
                .onFailure {
                    _eventFlow.emit(CreatePostEvent.Error("게시글 등록 실패: ${it.message}"))
                }
        } catch (e: Exception) {
            _eventFlow.emit(CreatePostEvent.Error("게시글 등록 중 예상치 못한 오류: ${e.message}"))
        } finally {
            _isLoading.value = false
        }
    }

    fun getCurrentUserUid(): String? = auth.currentUser?.uid
    fun getCurrentUserDisplayName(): String? = auth.currentUser?.displayName

    sealed class CreatePostEvent {
        data class PhotosUploaded(val photoUrls: List<String>) : CreatePostEvent()
        object PostCreated : CreatePostEvent()
        data class Error(val message: String) : CreatePostEvent()
    }
}
