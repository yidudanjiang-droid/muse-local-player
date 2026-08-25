package com.muse.localplayer.playback

sealed interface LibraryUiState {
    data object Idle : LibraryUiState
    data object Loading : LibraryUiState
    data object PermissionRequired : LibraryUiState
    data object Empty : LibraryUiState
    data class Ready(val trackCount: Int) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
}
