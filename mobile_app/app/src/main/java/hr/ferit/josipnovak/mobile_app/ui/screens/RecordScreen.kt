package hr.ferit.josipnovak.mobile_app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import hr.ferit.josipnovak.mobile_app.viewmodel.DetectionViewModel
import java.io.File

@Composable
fun RecordScreen(
    viewModel: DetectionViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val isLoading by viewModel.isLoading.collectAsState()
    val originalBitmap by viewModel.currentOriginalBitmap.collectAsState()
    val maskBitmap by viewModel.currentMaskBitmap.collectAsState()
    val segmentedBitmap by viewModel.currentSegmentedBitmap.collectAsState()
    val errorMsg by viewModel.errorMessage.collectAsState()

    val photoFile = remember { File(context.getExternalFilesDir(null), "fire_detection.jpg") }
    val photoUri = remember(photoFile) {
        FileProvider.getUriForFile(context, "${context.packageName}.provider", photoFile)
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            viewModel.processImage(photoFile)
        }
    }

    LaunchedEffect(errorMsg) {
        if (!errorMsg.isNullOrEmpty()) {
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Record Image", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            Text("Processing...")
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (originalBitmap != null && maskBitmap != null && segmentedBitmap != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Original", style = MaterialTheme.typography.bodySmall)
                    Image(
                        bitmap = originalBitmap!!.asImageBitmap(),
                        contentDescription = "Original",
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).border(1.dp, Color.Gray).padding(4.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Mask", style = MaterialTheme.typography.bodySmall)
                    Image(
                        bitmap = maskBitmap!!.asImageBitmap(),
                        contentDescription = "Mask",
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).border(1.dp, Color.Gray).padding(4.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Segmented", style = MaterialTheme.typography.bodySmall)
                    Image(
                        bitmap = segmentedBitmap!!.asImageBitmap(),
                        contentDescription = "Segmented",
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).border(1.dp, Color.Gray).padding(4.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Button(onClick = { 
                    viewModel.saveDetectionToFirebase {
                        Toast.makeText(context, "Saved Successfully!", Toast.LENGTH_SHORT).show()
                        viewModel.resetCurrentDetection()
                        onNavigateBack()
                    }
                }) {
                    Text("Save to Firebase")
                }
                OutlinedButton(onClick = { viewModel.resetCurrentDetection() }) {
                    Text("Retry")
                }
            }
        } else if (!isLoading) {
            Button(
                onClick = { cameraLauncher.launch(photoUri) },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Take Picture")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onNavigateBack) {
            Text("Back")
        }
    }
}
