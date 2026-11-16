package com.wowstudio.claudephotosorter

data class PhotoItem(
    val uri: String,
    val displayName: String,
    val category: String = "Not analyzed",
    val labels: List<String> = emptyList(),
    val sha256: String? = null,
    val duplicate: Boolean = false,
    val error: String? = null
)
