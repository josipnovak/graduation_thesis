package hr.ferit.josipnovak.mobile_app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MainScreen(
    onNavigateToRecord: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Fire Detection System",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 48.dp)
        )

        Button(
            onClick = onNavigateToRecord,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Record Image & Detect Fire")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onNavigateToHistory,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Show Past Detections")
        }
    }
}
