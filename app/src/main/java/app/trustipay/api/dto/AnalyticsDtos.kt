package app.trustipay.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SummaryPoint(
    val bucket: String,
    val income: Double,
    val expense: Double
)

@JsonClass(generateAdapter = true)
data class SummaryReportResponse(
    @Json(name = "user_ref") val userRef: String,
    @Json(name = "from") val fromDate: String,
    @Json(name = "to") val toDate: String,
    @Json(name = "group_by") val groupBy: String,
    @Json(name = "income_total") val incomeTotal: Double,
    @Json(name = "expense_total") val expenseTotal: Double,
    @Json(name = "net_total") val netTotal: Double,
    val series: List<SummaryPoint>
)

@JsonClass(generateAdapter = true)
data class CategoryTotal(
    val category: String,
    val total: Double,
    val count: Int,
    val percentage: Double
)

@JsonClass(generateAdapter = true)
data class CategoriesReportResponse(
    @Json(name = "user_ref") val userRef: String,
    @Json(name = "from") val fromDate: String,
    @Json(name = "to") val toDate: String,
    val items: List<CategoryTotal>,
    @Json(name = "expense_total") val expenseTotal: Double
)

@JsonClass(generateAdapter = true)
data class FhsReportResponse(
    @Json(name = "user_ref") val userRef: String,
    val score: Int,
    val interpretation: String,
    val band: String,
    @Json(name = "positive_drivers") val positiveDrivers: List<String>,
    @Json(name = "negative_drivers") val negativeDrivers: List<String>
)

@JsonClass(generateAdapter = true)
data class RecommendationItem(
    val title: String,
    val priority: String,
    val component: String,
    val message: String,
    val reason: String,
    @Json(name = "estimated_impact") val estimatedImpact: String,
    @Json(name = "action_type") val actionType: String,
    val rank: Int
)

@JsonClass(generateAdapter = true)
data class RecommendationsReportResponse(
    @Json(name = "user_ref") val userRef: String,
    @Json(name = "fhs_score") val fhsScore: Int,
    val interpretation: String,
    val items: List<RecommendationItem>
)

@JsonClass(generateAdapter = true)
data class BehaviorProfileResponse(
    @Json(name = "user_ref") val userRef: String,
    val cluster: Int,
    val label: String,
    val summary: String,
    val traits: List<String>
)
