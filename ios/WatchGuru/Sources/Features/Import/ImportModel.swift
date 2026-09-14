import Foundation
import Observation
import WatchGuruAPI

/// Where an import is up to.
///
/// Genuinely exclusive states rather than a pile of flags: a file cannot be
/// being read while a preview is on screen, and the result replaces the
/// preview rather than sitting underneath it.
enum ImportStage {
    /// Nothing chosen yet. What the screen opens on.
    case choosing
    case reading
    case previewing(ImportPreviewResponse)
    case committing(ImportPreviewResponse)
    case finished(ImportResultResponse)
}

/// Reading somebody's history in from a file they exported somewhere else.
///
/// The two server calls are deliberately two: the preview writes nothing to
/// the user's library, and the commit writes only what is still selected when
/// they press the button. That is why the selection lives here rather than
/// being worked out at send time — an import nobody got to disagree with is
/// worse than no import, because undoing four hundred wrong rows by hand is
/// the worst first hour this app could offer.
@Observable
@MainActor
final class ImportModel {

    private let client: WatchGuruClient

    var stage: ImportStage = .choosing
    var failureMessage: String?

    /// Every row that will be sent, and the title it will be recorded against.
    ///
    /// A row is included exactly when it has an entry here, so a matched row
    /// switched off and an ambiguous row never resolved are the same thing to
    /// the commit — which is what stops an unresolved row being sent against a
    /// title the app picked and the user never saw.
    private(set) var chosen: [String: Int64] = [:]

    init(client: WatchGuruClient) {
        self.client = client
    }

    var selectedCount: Int { chosen.count }

    func isSelected(_ row: ImportRowResponse) -> Bool { chosen[row.sourceRef] != nil }

    func chosenTitle(for row: ImportRowResponse) -> Int64? { chosen[row.sourceRef] }

    func fileChosen(_ url: URL) async {
        stage = .reading
        // Off the main actor: `ImportFile` allows a five-megabyte file, and
        // reading and decoding one where the interface is drawn is a visible
        // hitch on the screen that is meant to be saying it is working.
        let read = await Task.detached { ImportFile.read(at: url) }.value
        let text: String
        switch read {
        case .success(let contents):
            text = contents
        case .failure(let problem):
            failureMessage = problem.message
            stage = .choosing
            return
        }
        do {
            let preview = try await client.previewImport(content: text)
            chosen = Dictionary(
                uniqueKeysWithValues: preview.rows.compactMap { row -> (String, Int64)? in
                    guard row.status == .matched, let titleId = row.titleId else { return nil }
                    return (row.sourceRef, titleId)
                }
            )
            stage = .previewing(preview)
        } catch {
            failureMessage = String(localized: "The file could not be checked. Try again.")
            stage = .choosing
        }
    }

    /// Turns a resolved row off, or back on against the title it resolved to.
    func toggle(_ row: ImportRowResponse) {
        if chosen[row.sourceRef] != nil {
            chosen[row.sourceRef] = nil
        } else if let resolved = row.titleId ?? row.candidates.first?.titleId {
            chosen[row.sourceRef] = resolved
        }
    }

    /// Settles an ambiguous row on one of the candidates.
    ///
    /// Choosing also selects the row: somebody who has just said *that one*
    /// has said everything a second tap would say.
    func choose(_ row: ImportRowResponse, titleId: Int64) {
        chosen[row.sourceRef] = titleId
    }

    func commit() async {
        guard case .previewing(let preview) = stage else { return }
        let selections = preview.rows.compactMap { row -> ImportSelection? in
            guard let titleId = chosen[row.sourceRef] else { return nil }
            // Argument order is the generated initialiser's, which is
            // alphabetical; Swift requires the call to match the declaration.
            //
            // `watchedAt` is handed straight back rather than converted: it is
            // the server's own value returning to it, and a timezone shift
            // applied on the way out would move a viewing to the day before.
            return ImportSelection(
                episodeId: row.episodeId,
                rating: row.rating,
                sourceRef: row.sourceRef,
                titleId: titleId,
                titleText: row.titleText,
                watchedAt: row.watchedAt
            )
        }
        guard !selections.isEmpty else { return }

        stage = .committing(preview)
        do {
            stage = .finished(try await client.commitImport(rows: selections))
        } catch {
            failureMessage = String(localized: "That import did not go through. Nothing was added.")
            stage = .previewing(preview)
        }
    }

    /// Back to the start, for a second file.
    func startOver() {
        chosen = [:]
        stage = .choosing
    }
}

/// Turning a chosen file into text, off the main actor.
///
/// Outside `ImportModel` rather than nested in it: a type nested in a
/// `@MainActor` class inherits that isolation, and a failure that cannot leave
/// the main actor is no use to a read that deliberately happens away from it.
enum ImportFile {

    /// The server's own cap on a preview body, checked here so an oversized
    /// export is refused before it is uploaded over a phone connection.
    static let maximumCharacters = 5_000_000

    /// Why a file could not be turned into rows, before the server sees it.
    enum ReadFailure: Error, Sendable {
        case unreadable
        case tooLarge

        var message: String {
            switch self {
            case .unreadable: return String(localized: "That file could not be read.")
            case .tooLarge: return String(localized: "That file is too large to import.")
            }
        }
    }

    /// Reads the whole file as text.
    ///
    /// The picker hands back a URL the app is not otherwise allowed to open,
    /// so the security-scoped resource has to be claimed and released around
    /// the read — without that, a file chosen from iCloud Drive or another
    /// app's container fails with a permission error rather than a missing one.
    static func read(at url: URL) -> Result<String, ReadFailure> {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }

        guard let data = try? Data(contentsOf: url) else { return .failure(.unreadable) }
        // Exports are UTF-8 in practice; a Latin-1 fallback costs nothing and
        // turns a file that would simply fail into one the parser can judge.
        guard let text = String(data: data, encoding: .utf8)
            ?? String(data: data, encoding: .isoLatin1) else {
            return .failure(.unreadable)
        }
        guard text.count <= maximumCharacters else { return .failure(.tooLarge) }
        return .success(text)
    }
}
