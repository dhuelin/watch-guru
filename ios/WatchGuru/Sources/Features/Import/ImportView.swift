import SwiftUI
import UniformTypeIdentifiers
import WatchGuruAPI

/// Bringing an existing history in from a file.
///
/// The screen is built around one refusal: it will not write anything the user
/// has not looked at. A file becomes a list they can argue with, and only what
/// survives that is sent. Matched rows start selected, because agreeing with
/// four hundred correct rows one at a time is not a review but a chore;
/// ambiguous ones start unselected, because the app guessing on somebody's
/// behalf is the failure this two-step shape exists to avoid.
struct ImportView: View {

    @Environment(Session.self) private var session
    @State private var model: ImportModel?
    @State private var picking = false

    var body: some View {
        Group {
            if let model {
                content(model: model)
            } else {
                ProgressView()
            }
        }
        .navigationTitle("Import")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            if model == nil {
                model = ImportModel(client: session.client)
            }
        }
        .fileImporter(
            isPresented: $picking,
            allowedContentTypes: Self.readableTypes,
            allowsMultipleSelection: false
        ) { result in
            guard case .success(let urls) = result, let url = urls.first, let model else { return }
            Task { await model.fileChosen(url) }
        }
        .alert("That did not work", isPresented: Binding(
            get: { model?.failureMessage != nil },
            set: { if !$0 { model?.failureMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(model?.failureMessage ?? "")
        }
    }

    /// What the picker will let through.
    ///
    /// `.data` is last and deliberate: several providers hand back a `.csv`
    /// with no better type than that, and a picker greying out the file
    /// somebody just downloaded is a dead end with no explanation.
    private static let readableTypes: [UTType] = [.commaSeparatedText, .plainText, .text, .data]

    @ViewBuilder
    private func content(model: ImportModel) -> some View {
        switch model.stage {
        case .choosing:
            chooseFile
        case .reading:
            busy("Reading the file…")
        case .committing:
            busy("Importing…")
        case .previewing(let preview):
            PreviewList(preview: preview, model: model)
        case .finished(let result):
            finished(result: result, model: model)
        }
    }

    private var chooseFile: some View {
        List {
            Section {
                Text("Bring in what you have already watched. Nothing is added to your library until you have seen what the file contains and said yes.")
                Button("Choose a file") { picking = true }
            }
            Section("Files this reads") {
                Text("IMDb (Lists or Ratings → Export), Letterboxd (Settings → Import & Export), Netflix (Viewing activity → Download all), and Watch Guru's own CSV. The format is worked out from the file, so there is nothing to pick.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func busy(_ message: LocalizedStringKey) -> some View {
        VStack(spacing: 16) {
            ProgressView()
            Text(message)
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func finished(result: ImportResultResponse, model: ImportModel) -> some View {
        List {
            Section {
                Text("\(result.imported) added, \(result.skipped) already there.")
                if result.failed > 0 {
                    Text("\(result.failed) could not be written.")
                        .foregroundStyle(.red)
                }
            } header: {
                Text("Imported")
            }
            if !result.problems.isEmpty {
                Section("Rows that could not be read") {
                    ForEach(result.problems, id: \.self) { problem in
                        Text(problem)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            Section {
                Button("Import another file") { model.startOver() }
            }
        }
    }
}

/// The file, as a list the user can disagree with.
///
/// Rows are grouped by what they need from the reader rather than left in file
/// order: a matched row needs a glance, an ambiguous one needs a decision, and
/// an unmatched one needs nothing at all. Interleaved, the decisions hide among
/// four hundred rows that do not need any.
private struct PreviewList: View {

    let preview: ImportPreviewResponse
    let model: ImportModel

    var body: some View {
        VStack(spacing: 0) {
            List {
                Section {
                    Text(Self.sourceDescription(preview.source))
                    if preview.alreadyImported > 0 {
                        Text("\(preview.alreadyImported) already imported. These are skipped.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }

                if !preview.warnings.isEmpty {
                    notes("Worth knowing about this file", preview.warnings)
                }

                if preview.rows.isEmpty {
                    Section {
                        Text("Nothing in this file could be read as a watch history.")
                    }
                }

                if !ambiguous.isEmpty {
                    Section {
                        ForEach(ambiguous, id: \.sourceRef) { row in
                            AmbiguousRow(row: row, model: model)
                        }
                    } header: {
                        Text("Needs a choice")
                    } footer: {
                        Text("More than one title matches. Pick the right one, or leave it out.")
                    }
                }

                if !matched.isEmpty {
                    Section("Ready to import") {
                        ForEach(matched, id: \.sourceRef) { row in
                            MatchedRow(row: row, model: model)
                        }
                    }
                }

                if !unmatched.isEmpty {
                    Section {
                        ForEach(unmatched, id: \.sourceRef) { row in
                            Text(Self.label(for: row))
                                .foregroundStyle(.secondary)
                        }
                    } header: {
                        Text("Not found")
                    } footer: {
                        Text("These could not be matched to a title, so they cannot be imported. Nothing is lost — you can add them by hand later.")
                    }
                }

                if !preview.problems.isEmpty {
                    notes("Rows that could not be read", preview.problems)
                }
            }

            Divider()
            Button {
                Task { await model.commit() }
            } label: {
                Text(model.selectedCount == 0
                     ? "Nothing selected"
                     : "Import \(model.selectedCount)")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .disabled(model.selectedCount == 0)
            .padding()
        }
    }

    private var matched: [ImportRowResponse] { preview.rows.filter { $0.status == .matched } }
    private var ambiguous: [ImportRowResponse] { preview.rows.filter { $0.status == .ambiguous } }
    private var unmatched: [ImportRowResponse] { preview.rows.filter { $0.status == .unmatched } }

    private func notes(_ title: LocalizedStringKey, _ lines: [String]) -> some View {
        Section(title) {
            ForEach(lines, id: \.self) { line in
                Text(line)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
    }

    /// The file's own words for a row, which is all an unmatched one has.
    static func label(for row: ImportRowResponse) -> String {
        guard let year = row.year else { return row.titleText }
        return "\(row.titleText) (\(year))"
    }

    /// One key per source rather than a name substituted into a sentence:
    /// "Read as %@" reads badly in a language that declines the noun, and
    /// there are only five of them.
    static func sourceDescription(_ source: ImportPreviewResponse.Source) -> LocalizedStringKey {
        switch source {
        case .watchGuru: return "Read as a Watch Guru export"
        case .imdb: return "Read as an IMDb export"
        case .letterboxd: return "Read as a Letterboxd export"
        case .netflix: return "Read as a Netflix export"
        case .unknown: return "Read as an unrecognised file"
        }
    }
}

/// A row that resolved cleanly, and can simply be switched off.
private struct MatchedRow: View {

    let row: ImportRowResponse
    let model: ImportModel

    var body: some View {
        Button {
            model.toggle(row)
        } label: {
            HStack {
                // The row is the control: the tick is what it looks like, not
                // a second smaller control saying the same thing.
                Image(systemName: model.isSelected(row) ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(model.isSelected(row) ? Color.accentColor : .secondary)
                VStack(alignment: .leading, spacing: 2) {
                    Text(row.titleName ?? row.titleText)
                    if let detail = Self.detail(for: row) {
                        Text(detail)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(model.isSelected(row) ? .isSelected : [])
    }

    /// Episode, date, rating, and anything the matcher wants to say.
    static func detail(for row: ImportRowResponse) -> String? {
        var parts: [String] = []
        if let code = row.episodeCode { parts.append(code) }
        if let watchedAt = row.watchedAt {
            parts.append(watchedAt.formatted(date: .abbreviated, time: .omitted))
        }
        if let rating = row.rating {
            parts.append(rating.formatted(.number.precision(.fractionLength(0...1))))
        }
        if let note = row.note { parts.append(note) }
        return parts.isEmpty ? nil : parts.joined(separator: "  ·  ")
    }
}

/// A row the server could not settle, and the candidates it found.
///
/// Nothing is preselected. The app choosing here and the user not noticing is
/// precisely how a library ends up with the wrong *Fargo* in it.
private struct AmbiguousRow: View {

    let row: ImportRowResponse
    let model: ImportModel

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(PreviewList.label(for: row))
            ForEach(row.candidates, id: \.titleId) { candidate in
                Button {
                    model.choose(row, titleId: candidate.titleId)
                } label: {
                    HStack {
                        Image(systemName: model.chosenTitle(for: row) == candidate.titleId
                              ? "largecircle.fill.circle"
                              : "circle")
                            .foregroundStyle(model.chosenTitle(for: row) == candidate.titleId
                                             ? Color.accentColor
                                             : .secondary)
                        Text(Self.candidateLabel(candidate))
                            .font(.subheadline)
                    }
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.vertical, 4)
    }

    static func candidateLabel(_ candidate: ImportCandidateResponse) -> String {
        guard let year = candidate.year else { return candidate.titleName }
        return "\(candidate.titleName) (\(year))"
    }
}
