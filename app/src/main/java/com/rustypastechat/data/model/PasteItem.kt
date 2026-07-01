package com.rustypastechat.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PasteItem(
    @SerialName("file_name") val fileName: String,
    @SerialName("file_size") val fileSize: Long,
    @SerialName("creation_date_utc") val creationDateUtc: String? = null,
    @SerialName("expires_at_utc") val expiresAtUtc: String? = null
)
