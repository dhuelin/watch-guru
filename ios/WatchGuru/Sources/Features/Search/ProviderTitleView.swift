import SwiftUI
import WatchGuruAPI

/// A title as the provider names it: TMDB's id and the kind of thing it is.
///
/// Its own navigation value rather than a bare `Int64`, because a search hit's
/// id and a catalogue id are both `Int64` and mean entirely different things.
/// Pushed as a number, a TMDB id was read as one of ours — which opened
/// nothing when no title had that id, and would have opened a different title
/// when one did. With a distinct type, a `navigationDestination(for: Int64.self)`
/// cannot receive one by accident.
struct ProviderTitle: Hashable {
    let providerId: Int64
    let titleType: SearchHit.TitleType

    init(_ hit: SearchHit) {
        providerId = hit.providerId
        titleType = hit.titleType
    }

    /// The same two cases under the name the import operation gives them.
    var importType: TitleControllerAPI.TitleType_importTitle {
        switch titleType {
        case .movie: .movie
        case .tvSeries: .tvSeries
        }
    }
}

/// Resolves a provider title to the catalogue's own, then shows it.
///
/// Importing is the resolution: the server returns the row it already has, or
/// fetches the title and creates one, and either way the answer carries the id
/// the detail screen and every write on it need.
struct ProviderTitleView: View {

    let title: ProviderTitle

    @Environment(Session.self) private var session
    @State private var model: ProviderTitleModel?

    var body: some View {
        Group {
            if let model {
                switch model.state {
                case .content(let titleId), .refreshing(let titleId):
                    TitleDetailView(titleId: titleId)
                case .failed(let failure):
                    // Offline lands here too, and says so: there is no snapshot
                    // of a title nobody has opened, and the detail screen would
                    // need the network for it anyway.
                    FailureView(failure: failure) { Task { await model.resolve() } }
                case .loading, .empty:
                    ProgressView()
                }
            } else {
                ProgressView()
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .task {
            if model == nil {
                let created = ProviderTitleModel(client: session.client, title: title)
                model = created
                await created.resolve()
            }
        }
    }
}

@Observable
@MainActor
final class ProviderTitleModel {

    /// The catalogue id, once the server has given one.
    private(set) var state: ViewState<Int64> = .loading

    private let client: WatchGuruClient
    private let title: ProviderTitle

    init(client: WatchGuruClient, title: ProviderTitle) {
        self.client = client
        self.title = title
    }

    func resolve() async {
        state = .loading
        do {
            // Hoisted rather than inlined into the case; see TitleDetailModel.
            let resolved = try await client.importTitle(
                providerId: title.providerId, titleType: title.importType)
            state = .content(resolved.id)
        } catch {
            state = .failed(error)
        }
    }
}
