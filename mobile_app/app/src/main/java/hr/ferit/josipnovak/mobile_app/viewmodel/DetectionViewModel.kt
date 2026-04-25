package hr.ferit.josipnovak.mobile_app.viewmodel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import hr.ferit.josipnovak.mobile_app.model.DetectionRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class DetectionViewModel : ViewModel() {
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance("gs://fire-app-3ea13.firebasestorage.app")
    private val client = OkHttpClient()

    private val _records = MutableStateFlow<List<DetectionRecord>>(emptyList())
    val records: StateFlow<List<DetectionRecord>> = _records.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _currentOriginalBitmap = MutableStateFlow<Bitmap?>(null)
    val currentOriginalBitmap: StateFlow<Bitmap?> = _currentOriginalBitmap.asStateFlow()

    private val _currentMaskBitmap = MutableStateFlow<Bitmap?>(null)
    val currentMaskBitmap: StateFlow<Bitmap?> = _currentMaskBitmap.asStateFlow()

    private val _currentSegmentedBitmap = MutableStateFlow<Bitmap?>(null)
    val currentSegmentedBitmap: StateFlow<Bitmap?> = _currentSegmentedBitmap.asStateFlow()

    private val _fireDetected = MutableStateFlow<Boolean?>(null)
    val fireDetected: StateFlow<Boolean?> = _fireDetected.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun fetchHistory() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val snapshot = firestore.collection("detections")
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .get().await()
                val list = snapshot.documents.mapNotNull { it.toObject(DetectionRecord::class.java)?.copy(id = it.id) }
                _records.value = list
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load history: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun processImage(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val originalBitmap = BitmapFactory.decodeFile(file.absolutePath)
                _currentOriginalBitmap.value = originalBitmap

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", file.name, file.asRequestBody("image/jpeg".toMediaTypeOrNull()))
                    .build()

                val request = Request.Builder()
                    .url("http://10.121.0.188:8000/detect")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val jsonString = response.body?.string()
                    if (!jsonString.isNullOrEmpty()) {
                        try {
                            val jsonObject = org.json.JSONObject(jsonString)
                            
                            val maskBase64 = jsonObject.optString("mask").substringAfter("base64,")
                            val segmentedBase64 = jsonObject.optString("segmented_image").substringAfter("base64,")

                            if (jsonObject.has("fire_detected")) {
                                _fireDetected.value = jsonObject.getBoolean("fire_detected")
                            }

                            if (maskBase64.isNotEmpty() && segmentedBase64.isNotEmpty()) {
                                val maskBytes = android.util.Base64.decode(maskBase64, android.util.Base64.DEFAULT)
                                val maskBitmap = BitmapFactory.decodeByteArray(maskBytes, 0, maskBytes.size)
                                _currentMaskBitmap.value = maskBitmap

                                val segmentedBytes = android.util.Base64.decode(segmentedBase64, android.util.Base64.DEFAULT)
                                val segmentedBitmap = BitmapFactory.decodeByteArray(segmentedBytes, 0, segmentedBytes.size)
                                _currentSegmentedBitmap.value = segmentedBitmap
                            } else {
                                _errorMessage.value = "Missing image data in response"
                            }
                        } catch (e: Exception) {
                            _errorMessage.value = "Failed to parse response: ${e.message}"
                        }
                    } else {
                        _errorMessage.value = "Empty response from server"
                    }
                } else {
                    _errorMessage.value = "Error ${response.code}: ${response.body?.string()}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Connection error: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveDetectionToFirebase(onSuccess: () -> Unit) {
        val original = _currentOriginalBitmap.value ?: return
        val mask = _currentMaskBitmap.value ?: return
        val segmented = _currentSegmentedBitmap.value ?: return

        viewModelScope.launch {
            _isSaving.value = true
            try {
                val id = UUID.randomUUID().toString()
                
                val originalUrl = uploadBitmap(original, "original_$id.jpg")
                val maskUrl = uploadBitmap(mask, "mask_$id.png")
                val segmentedUrl = uploadBitmap(segmented, "segmented_$id.png")

                val record = DetectionRecord(
                    id = id,
                    timestamp = System.currentTimeMillis(),
                    originalImageUrl = originalUrl,
                    maskImageUrl = maskUrl,
                    segmentedImageUrl = segmentedUrl 
                )
                
                firestore.collection("detections").document(id).set(record).await()
                
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to save to Firebase: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    private suspend fun uploadBitmap(bitmap: Bitmap, filename: String): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
        val data = baos.toByteArray()
        val ref = storage.reference.child("images/$filename")
        
        return try {
            ref.putBytes(data).await()
            ref.downloadUrl.await().toString()
        } catch (e: Exception) {
            Log.e("FirebaseUpload", "Failed to upload $filename: ${e.message}", e)
            throw e
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun resetCurrentDetection() {
        _currentOriginalBitmap.value = null
        _currentMaskBitmap.value = null
        _currentSegmentedBitmap.value = null
        _fireDetected.value = null
    }

    fun getRecordById(id: String): DetectionRecord? {
        return _records.value.find { it.id == id }
    }
}
