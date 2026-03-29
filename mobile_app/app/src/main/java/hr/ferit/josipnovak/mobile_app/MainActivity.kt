package hr.ferit.josipnovak.mobile_app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FireDetectionScreen()
                }
            }
        }
    }
}

class FireDetectionViewModel {
    val resultBitmap = mutableStateOf<Bitmap?>(null)
    val isLoading = mutableStateOf(false)
}

val detectionViewModel = FireDetectionViewModel()

@Composable
fun FireDetectionScreen() {
    val context = LocalContext.current
    val bitmapState = detectionViewModel.resultBitmap
    val isLoading = detectionViewModel.isLoading

    val photoFile = remember { File(context.getExternalFilesDir(null), "fire_detection.jpg") }
    val photoUri = remember(photoFile) {
        FileProvider.getUriForFile(context, "${context.packageName}.provider", photoFile)
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            isLoading.value = true
            uploadImage(photoFile,
                onResult = { bitmap ->
                    bitmapState.value = bitmap
                    isLoading.value = false
                },
                onError = { error ->
                    isLoading.value = false
                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Fire Detection System",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        if (bitmapState.value != null) {
            Text(
                text = "Analysis Complete",
                color = Color(0xFF4CAF50),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Image(
                bitmap = bitmapState.value!!.asImageBitmap(),
                contentDescription = "Detection Result",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)
                    .border(2.dp, Color.Gray)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { bitmapState.value = null },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reset")
            }
        } else {
            if (isLoading.value) {
                CircularProgressIndicator(modifier = Modifier.size(50.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Processing image...")
            } else {
                Text(
                    text = "No image analyzed yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                Button(
                    onClick = { cameraLauncher.launch(photoUri) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("Detect Fire")
                }
            }
        }
    }
}

fun uploadImage(file: File, onResult: (Bitmap) -> Unit, onError: (String) -> Unit) {
    val client = OkHttpClient()

    val requestBody = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("file", file.name, file.asRequestBody("image/jpeg".toMediaTypeOrNull()))
        .build()

    val request = Request.Builder()
        .url("http://192.168.0.8:8000/detect")
        .post(requestBody)
        .build()

    CoroutineScope(Dispatchers.IO).launch {
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorMsg = response.body?.string() ?: "Unknown server error"
                    withContext(Dispatchers.Main) { onError("Error ${response.code}: $errorMsg") }
                    return@launch
                }

                val bytes = response.body?.bytes()
                if (bytes != null && bytes.isNotEmpty()) {
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        withContext(Dispatchers.Main) { onResult(bitmap) }
                    } else {
                        withContext(Dispatchers.Main) { onError("Failed to decode image") }
                    }
                } else {
                    withContext(Dispatchers.Main) { onError("Empty response from server") }
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError("Connection error: ${e.localizedMessage}")
            }
        }
    }
}