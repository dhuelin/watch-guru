import SwiftUI
import WatchGuruAPI

struct HistoryView: View {

    @Environment(Session.self) private var session
    @State private var model: HistoryModel?

    var body: some View {
        Group {
            if let model {
                content(model)
            } else {
                ProgressView()
            }
        }
        .navigationTitle("History")
        .task {
            if model == nil {
                let created = HistoryModel(client: session.client)
                model = created
                await created.load()
            }
        }
    }

    @ViewBuilder
    private func content(_ model: HistoryModel) -> some View {
        if model.state.value != nil {
            List {
                ForEach(model.groupedByDay, id: \.day) { group in
                    Section(group.day.formatted(.dateTime.day().month(.wide).year())) {
                        ForEach(group.events, id: \.id) { event in
                            HistoryRow(event: event)
                                .swipeActions(edge: .trailing) {
                                    Button(role: .destructive) {
                                        Task { await model.delete(event) }
                                    } label: {
                                        Label("Delete", systemImage: "trash")
                                    }
                                }
                        }
                    }
                }
            }
            .listStyle(.insetGrouped)
            .refreshable { await model.load() }
        } else {
            switch model.state {
            case .loading:
                ProgressView()
            case .failed(let failure):
                FailureView(failure: failure) { Task { await model.load() } }
            default:
                ContentUnavailableView(
                    "Nothing watched yet",
                    systemImage: "clock.arrow.circlepath",
                    description: Text("Marking an episode will show it here.")
                )
            }
        }
    }
}

private struct HistoryRow: View {
    let event: WatchEventResponse

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(event.primaryTitle).font(.body).lineLimit(1)
            if !subtitle.isEmpty {
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
    }

    private var subtitle: String {
        var parts: [String] = []
        if let code = event.episodeCode { parts.append(code) }
        if let name = event.episodeName { parts.append(name) }
        // A rewatch is marked as such: this is a record of viewings, not of
        // first viewings.
        if event.rewatch { parts.append("rewatch") }
        if let service = event.streamingServiceName { parts.append(service) }
        return parts.joined(separator: " · ")
    }
}
