package dev.dhuelin.watchguru.ui.components

import dev.dhuelin.watchguru.data.ApiResult

/**
 * What a screen is showing right now.
 *
 * Four states, all designed: see docs/DESIGN.md. A spinner over a blank screen
 * is not a loading state, it is the absence of one.
 *
 * [Refreshing] exists separately from [Loading] because a cached screen must
 * keep showing its content while it updates underneath -- replacing a populated
 * library with a spinner every time the user returns to it is worse than
 * showing data a few seconds stale.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Refreshing<T>(val stale: T) : UiState<T>
    data class Content<T>(val value: T) : UiState<T>
    data object Empty : UiState<Nothing>
    data class Error(val failure: ApiResult.Failure) : UiState<Nothing>
}

/** The content to render, if any, including while a refresh is in flight. */
fun <T> UiState<T>.contentOrNull(): T? = when (this) {
    is UiState.Content -> value
    is UiState.Refreshing -> stale
    else -> null
}
