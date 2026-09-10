package com.dhuelin.dev.watchguru.api;

import com.dhuelin.dev.watchguru.api.dto.Requests;
import com.dhuelin.dev.watchguru.api.dto.Responses;
import com.dhuelin.dev.watchguru.config.ImportProperties;
import com.dhuelin.dev.watchguru.imports.domain.ImportRow;
import com.dhuelin.dev.watchguru.imports.domain.MatchedRow;
import com.dhuelin.dev.watchguru.imports.service.ImportService;
import com.dhuelin.dev.watchguru.security.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Reading an existing viewing history in from another service.
 *
 * <p>Two calls, never one. The preview is what the user agrees to; the commit
 * writes only what came back from them. An import that silently marked four
 * hundred titles watched -- some of them wrongly -- would be worse than no
 * import at all, and undoing it by hand is the worst first hour a tracking app
 * can offer.
 */
@RestController
@RequestMapping("/api/v1/me/imports")
public class ImportController {

    private final CurrentUserService currentUser;
    private final ImportService imports;
    private final ImportProperties properties;

    public ImportController(CurrentUserService currentUser,
                            ImportService imports,
                            ImportProperties properties) {
        this.currentUser = currentUser;
        this.imports = imports;
        this.properties = properties;
    }

    /** Reads a file and says what it would do. Writes nothing. */
    @PostMapping("/preview")
    @Operation(operationId = "previewImport")
    public Responses.ImportPreviewResponse preview(@Valid @RequestBody Requests.PreviewImport request) {
        ImportService.Preview preview = imports.preview(
                currentUser.require(), request.content(), properties.providerLookupsPerImport());

        return new Responses.ImportPreviewResponse(
                preview.source(),
                preview.rows().stream().map(ImportController::toRow).toList(),
                preview.problems(),
                preview.warnings(),
                preview.alreadyImported());
    }

    /** Writes the rows the user accepted. */
    @PostMapping
    @Operation(operationId = "commitImport")
    public Responses.ImportResultResponse commit(@Valid @RequestBody Requests.CommitImport request) {
        List<MatchedRow> rows = request.rows().stream()
                .map(ImportController::toMatched)
                .toList();

        ImportService.Result result = imports.commit(currentUser.require(), rows);
        return new Responses.ImportResultResponse(
                result.imported(), result.skipped(), result.failed(), result.problems());
    }

    private static Responses.ImportRowResponse toRow(MatchedRow matched) {
        ImportRow row = matched.row();
        return new Responses.ImportRowResponse(
                row.sourceRef(),
                row.titleText(),
                row.year(),
                row.imdbId(),
                row.seasonNumber(),
                row.episodeNumber(),
                row.episodeName(),
                row.watchedAt(),
                row.rating(),
                matched.status(),
                matched.titleId(),
                matched.titleName(),
                matched.episodeId(),
                matched.episodeCode(),
                matched.candidates().stream()
                        .map(c -> new Responses.ImportCandidateResponse(c.titleId(), c.titleName(), c.year()))
                        .toList(),
                matched.note());
    }

    /**
     * Rebuilds a row from what the client sent back.
     *
     * <p>Only the fields a write needs. Whether this was an episode is decided
     * by the presence of an episode id and nothing else: a row the user
     * accepted has already been resolved, and a client that sends a title with
     * no episode is asking for the title to be recorded.
     *
     * <p>The title id is taken as given, because for an ambiguous row it is
     * the user's own choice between the candidates they were shown, and a
     * catalogue title is public data -- there is nothing here that one user
     * could name and another may not.
     */
    private static MatchedRow toMatched(Requests.ImportSelection selection) {
        ImportRow row = new ImportRow(
                selection.sourceRef(),
                selection.titleText() == null ? "" : selection.titleText(),
                null, null, null, null, null, null,
                selection.watchedAt(),
                null);

        return MatchedRow.matched(row, selection.titleId(), selection.titleText(),
                selection.episodeId(), null, null);
    }
}
