package com.rustypastechat.di

import com.rustypastechat.data.repository.LinkPreviewRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Lets leaf composables (message bubbles, deep inside the tree) reach the singleton
 * [LinkPreviewRepository] directly via [dagger.hilt.android.EntryPointAccessors] instead of
 * threading a callback prop through every intermediate composable between ChatScreen and here.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface LinkPreviewEntryPoint {
    fun linkPreviewRepository(): LinkPreviewRepository
}
