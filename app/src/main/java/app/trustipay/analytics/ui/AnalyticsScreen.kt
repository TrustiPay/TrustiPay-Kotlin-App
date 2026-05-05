package app.trustipay.analytics.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.trustipay.api.dto.*
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    modifier: Modifier = Modifier,
    viewModel: AnalyticsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Financial Insights", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.refreshAll() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = modifier.padding(innerPadding).fillMaxSize()) {
            if (uiState.isLoading && uiState.summary == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { FhsCard(uiState.fhs) }
                    item { BehaviorProfileCard(uiState.profile) }
                    item { SummarySection(uiState.summary) }
                    item { CategoriesSection(uiState.categories) }
                    item { RecommendationsSection(uiState.recommendations) }
                    
                    if (uiState.error != null) {
                        item {
                            ErrorCard(uiState.error!!)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FhsCard(fhs: FhsReportResponse?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Financial Health Score", style = MaterialTheme.typography.titleMedium)
                    Text(fhs?.band ?: "Calculating...", style = MaterialTheme.typography.bodySmall)
                }
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = (fhs?.score?.toFloat() ?: 0f) / 100f,
                        modifier = Modifier.size(64.dp),
                        strokeWidth = 6.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = fhs?.score?.toString() ?: "—",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (fhs != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = fhs.interpretation,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun BehaviorProfileCard(profile: BehaviorProfileResponse?) {
    if (profile == null) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(profile.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(profile.summary, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(profile.traits) { trait ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                        ) {
                            Text(
                                trait,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SummarySection(summary: SummaryReportResponse?) {
    Column {
        Text("Spending Overview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SummaryStat("Income", summary?.incomeTotal ?: 0.0, Color(0xFF4CAF50))
                    SummaryStat("Expense", summary?.expenseTotal ?: 0.0, Color(0xFFF44336))
                    SummaryStat("Net", summary?.netTotal ?: 0.0, MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.height(24.dp))
                // Simple Bar Chart
                SimpleBarChart(summary?.series ?: emptyList())
            }
        }
    }
}

@Composable
fun SummaryStat(label: String, value: Double, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(
            String.format("$%.2f", value),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun SimpleBarChart(series: List<SummaryPoint>) {
    if (series.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
            Text("No data available", style = MaterialTheme.typography.bodySmall)
        }
        return
    }

    val maxVal = series.maxOfOrNull { maxOf(it.income, it.expense) }?.takeIf { it > 0 } ?: 1.0
    
    Row(
        modifier = Modifier.fillMaxWidth().height(120.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        series.takeLast(10).forEach { point ->
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        Box(
                            modifier = Modifier
                                .width(8.dp)
                                .fillMaxHeight((point.income / maxVal).toFloat())
                                .background(Color(0xFF4CAF50).copy(alpha = 0.6f), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        )
                        Box(
                            modifier = Modifier
                                .width(8.dp)
                                .fillMaxHeight((point.expense / maxVal).toFloat())
                                .background(Color(0xFFF44336).copy(alpha = 0.6f), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        )
                    }
                }
                Text(
                    point.bucket.takeLast(2),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun CategoriesSection(categories: CategoriesReportResponse?) {
    if (categories == null || categories.items.isEmpty()) return
    Column {
        Text("Top Categories", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        categories.items.sortedByDescending { it.total }.take(5).forEach { cat ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(cat.category, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(
                    progress = (cat.percentage / 100f).toFloat(),
                    modifier = Modifier.width(100.dp).height(8.dp).clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    String.format("$%.0f", cat.total),
                    modifier = Modifier.width(60.dp),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun RecommendationsSection(recs: RecommendationsReportResponse?) {
    if (recs == null || recs.items.isEmpty()) return
    Column {
        Text("Recommendations", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        recs.items.forEach { item ->
            RecommendationCard(item)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun RecommendationCard(item: RecommendationItem) {
    val color = when(item.priority) {
        "high" -> Color(0xFFF44336)
        "medium" -> Color(0xFFFF9800)
        else -> Color(0xFF2196F3)
    }
    
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier.size(4.dp, 40.dp).background(color).clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(item.message, style = MaterialTheme.typography.bodySmall)
                Text(
                    "Impact: ${item.estimatedImpact}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun ErrorCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.width(12.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
