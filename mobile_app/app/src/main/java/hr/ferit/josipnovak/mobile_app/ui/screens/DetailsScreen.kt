package hr.ferit.josipnovak.mobile_app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import hr.ferit.josipnovak.mobile_app.viewmodel.DetectionViewModel

@Composable
fun DetailsScreen(
    id: String,
    viewModel: DetectionViewModel,
    onNavigateBack: () -> Unit
) {
    val record = remember(id) { viewModel.getRecordById(id) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        if (record == null) {
            Text("Record not found.")
        } else {
            Text("Detection Details", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))

            Text("Original Image", style = MaterialTheme.typography.titleMedium)
            AsyncImage(
                model = record.originalImageUrl,
                contentDescription = "Original",
                modifier = Modifier.fillMaxWidth().height(250.dp).padding(bottom = 16.dp)
            )

            Text("Mask Image", style = MaterialTheme.typography.titleMedium)
            AsyncImage(
                model = record.maskImageUrl,
                contentDescription = "Mask",
                modifier = Modifier.fillMaxWidth().height(250.dp).padding(bottom = 16.dp)
            )

            Text("Segmented Image", style = MaterialTheme.typography.titleMedium)
            AsyncImage(
                model = record.segmentedImageUrl,
                contentDescription = "Segmented",
                modifier = Modifier.fillMaxWidth().height(250.dp).padding(bottom = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}
