package hr.ferit.josipnovak.mobile_app.model

data class DetectionRecord(
    val id: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val originalImageUrl: String = "",
    val maskImageUrl: String = "",
    val segmentedImageUrl: String = ""
)
