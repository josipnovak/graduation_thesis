package hr.ferit.josipnovak.mobile_app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import hr.ferit.josipnovak.mobile_app.viewmodel.DetectionViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: DetectionViewModel,
    onNavigateToDetails: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val records by viewModel.records.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.fetchHistory()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Past Detections", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        } else if (records.isEmpty()) {
            Text("No past detections found.")
        } else {
            LazyColumn {
                items(records) { record ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable { onNavigateToDetails(record.id) },
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp)) {
                            AsyncImage(
                                model = record.originalImageUrl,
                                contentDescription = "Original",
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                Text("Detection At", style = MaterialTheme.typography.titleMedium)
                                Text(dateFormat.format(Date(record.timestamp)), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back to Main")
        }
    }
}
