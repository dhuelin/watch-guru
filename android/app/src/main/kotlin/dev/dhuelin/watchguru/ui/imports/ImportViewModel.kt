package dev.dhuelin.watchguru.ui.imports

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.dhuelin.watchguru.api.models.CommitImport
import dev.dhuelin.watchguru.api.models.ImportPreviewResponse
import dev.dhuelin.watchguru.api.models.ImportResultResponse
import dev.dhuelin.watchguru.api.models.ImportRowResponse
import dev.dhuelin.watchguru.api.models.ImportSelection
import dev.dhuelin.watchguru.data.ApiResult
import dev.dhuelin.watchguru.data.WatchGuruRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Where an import is up to.
 *
 * Four states rather than a pile of booleans, because they are genuinely
 * exclusive: a file cannot be being read while a preview is on screen, and the
 * result replaces the preview rather than sitting underneath it.
 */
sealed interface ImportStage {
    /** Nothing chosen yet. What the screen opens on. */
    data object Choosing : ImportStage
    data object Reading : ImportStage
    data class Previewing(val preview: ImportPreviewResponse) : ImportStage
    data class Committing(val preview: ImportPreviewResponse) : ImportStage
    data class Finished(val result: ImportResultResponse) : ImportStage
}

/**
 * Reading somebody's history in from a file they exported somewhere else.
 *
 * The two server calls are deliberately two: the preview writes nothing to the
 * user's library, and the commit writes only the rows still selected when they
 * pressed the button. That is the whole reason the selection lives here rather
 * than being inferred at send time -- an import the user did not get to
 * disagree with is worse than no import, because undoing four hundred wrong
 * rows by hand is the worst first hour this app could offer.
 */
@HiltViewModel
class ImportViewModel @Inject constructor(
    private val repository: WatchGuruRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _stage = MutableStateFlow<ImportStage>(ImportStage.Choosing)
    val stage: StateFlow<ImportStage> = _stage.asStateFlow()

    /**
     * Every row that will be sent, and the title it will be recorded against.
     *
     * A row is included exactly when it has an entry here, so a matched row
     * turned off and an ambiguous row never resolved are the same thing to the
     * commit -- which is what stops an unresolved row being sent against a
     * title the app picked and the user never saw.
     */
    private val _chosen = MutableStateFlow<Map<String, Long>>(emptyMap())
    val chosen: StateFlow<Map<String, Long>> = _chosen.asStateFlow()

    private val _error = MutableStateFlow<ImportError?>(null)
    val error: StateFlow<ImportError?> = _error.asStateFlow()

    /** What went wrong, in terms the screen turns into a sentence. */
    enum class ImportError { UNREADABLE, TOO_LARGE, PREVIEW_FAILED, COMMIT_FAILED }

    fun fileChosen(uri: Uri) {
        viewModelScope.launch {
            _stage.value = ImportStage.Reading
            val text = read(uri)
            if (text == null) {
                _stage.value = ImportStage.Choosing
                return@launch
            }
            when (val result = repository.previewImport(text)) {
                is ApiResult.Success -> {
                    _chosen.value = result.value.rows
                        .filter { it.status == ImportRowResponse.Status.MATCHED && it.titleId != null }
                        .associate { it.sourceRef to it.titleId!! }
                    _stage.value = ImportStage.Previewing(result.value)
                }
                is ApiResult.Failure -> {
                    _error.value = ImportError.PREVIEW_FAILED
                    _stage.value = ImportStage.Choosing
                }
            }
        }
    }

    /** Turns a resolved row off, or back on against the title it resolved to. */
    fun toggle(row: ImportRowResponse) {
        val titleId = _chosen.value[row.sourceRef]
        _chosen.value = if (titleId != null) {
            _chosen.value - row.sourceRef
        } else {
            val resolved = row.titleId ?: row.candidates.firstOrNull()?.titleId ?: return
            _chosen.value + (row.sourceRef to resolved)
        }
    }

    /**
     * Settles an ambiguous row on one of the candidates.
     *
     * Choosing also selects the row: somebody who has just said *that one* has
     * said everything a second tap would say.
     */
    fun choose(row: ImportRowResponse, titleId: Long) {
        _chosen.value = _chosen.value + (row.sourceRef to titleId)
    }

    fun commit() {
        val previewing = _stage.value as? ImportStage.Previewing ?: return
        val selections = previewing.preview.rows.mapNotNull { row ->
            val titleId = _chosen.value[row.sourceRef] ?: return@mapNotNull null
            ImportSelection(
                sourceRef = row.sourceRef,
                titleId = titleId,
                episodeId = row.episodeId,
                rating = row.rating,
                titleText = row.titleText,
                watchedAt = row.watchedAt,
            )
        }
        if (selections.isEmpty()) return

        viewModelScope.launch {
            _stage.value = ImportStage.Committing(previewing.preview)
            _stage.value = when (val result = repository.commitImport(CommitImport(rows = selections))) {
                is ApiResult.Success -> ImportStage.Finished(result.value)
                is ApiResult.Failure -> {
                    _error.value = ImportError.COMMIT_FAILED
                    previewing
                }
            }
        }
    }

    /** Back to the start, for a second file. */
    fun startOver() {
        _chosen.value = emptyMap()
        _stage.value = ImportStage.Choosing
    }

    fun dismissError() {
        _error.value = null
    }

    /**
     * Reads the whole file as text, or records why it could not.
     *
     * The length is checked here rather than left to the server so an
     * oversized export is refused before it is uploaded over a phone
     * connection. The limit is the server's own, so nothing this passes is
     * rejected on arrival for being too long.
     */
    private suspend fun read(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            }
            when {
                text == null -> {
                    _error.value = ImportError.UNREADABLE
                    null
                }
                text.length > MAX_CONTENT -> {
                    _error.value = ImportError.TOO_LARGE
                    null
                }
                else -> text
            }
        } catch (e: Exception) {
            // A document provider that has gone away, a revoked permission, a
            // file that is not there any more: all one thing to somebody who
            // just picked it out of a list.
            _error.value = ImportError.UNREADABLE
            null
        }
    }

    private companion object {
        /** Matches the server's own cap on a preview body. */
        const val MAX_CONTENT = 5_000_000
    }
}
