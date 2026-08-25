/*
 * Copyright 2024 Zakir Sheikh
 *
 * Derived from Audiofy's ContentResolver observe extension:
 * https://github.com/googol-apps/Audiofy
 * Licensed under the Apache License, Version 2.0.
 * See the root NOTICE and THIRD_PARTY_LICENSES/Audiofy-APACHE-2.0.txt.
 */
package com.muse.localplayer.data

import android.content.Context
import android.database.ContentObserver
import android.provider.MediaStore
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce

/**
 * Emits whenever the device audio MediaStore collection changes.
 * The implementation is adapted from Audiofy's Apache-2.0 ContentResolver observer pattern.
 */
class MediaStoreObserver(context: Context) {
    private val contentResolver = context.contentResolver

    @OptIn(FlowPreview::class)
    @Suppress("DEPRECATION")
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
        trySend(Unit)
        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }.debounce(750L)
}
