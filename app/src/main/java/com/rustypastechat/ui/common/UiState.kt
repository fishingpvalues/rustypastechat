package com.rustypastechat.ui.common

data class UiState<T : Any>(
    val data: T,
    val loading: Boolean = false,
    val error: OneTimeEvent<String?> = OneTimeEvent(null)
)
