import SwiftUI
import WatchGuruAPI

/// Which services the user pays for.
///
/// A flat list of toggles rather than a search field: there are a few dozen
/// services in a region, not a few thousand, and a list you can scan beats a
/// box you have to guess the spelling into.
///
/// Saved on every tap rather than behind a Save button. There is nothing to
/// validate and nothing to undo — the only effect is the order of a list on
/// another screen — and a Save button would invite the user to wonder what
/// happens if they do not press it.
///
/// Deliberately not a filter. Hiding titles the user cannot stream today would
/// hide the thing they were looking for the week before it arrives on their
/// service, and the explainer says so rather than leaving them to find out.
struct MyServicesView: View {

    @Environment(Session.self) private var session
    @State private var state: ViewState<[StreamingServiceResponse]> = .loading

    var body: some View {
        Group {
            switch state {
            case .loading, .refreshing:
                ProgressView()
            case .failed(let failure):
                FailureView(failure: failure) { Task { await load() } }
            case .empty:
                ContentUnavailableView(
                    "No services yet",
                    systemImage: "tv.slash",
                    description: Text("No streaming services are known for your region yet.")
                )
            case .content(let services):
                list(services)
            }
        }
        .navigationTitle("My services")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
    }

    private func list(_ services: [StreamingServiceResponse]) -> some View {
        List {
            Section {
                ForEach(services, id: \.id) { service in
                    Toggle(isOn: Binding(
                        get: { session.myServices.selected.contains(service.id) },
                        set: { _ in toggle(service.id) }
                    )) {
                        // The name alone. The only artwork type here is sized
                        // for a 2:3 poster, and a service logo is roughly
                        // square — it would come out stretched. Several of the
                        // seeded services carry no logo at all.
                        Text(service.name)
                    }
                }
            } footer: {
                Text("Tell us what you subscribe to and those services are listed first when you look at a title. Nothing is hidden either way.")
            }
        }
    }

    private func toggle(_ id: Int64) {
        var selected = session.myServices.selected
        if selected.contains(id) {
            selected.remove(id)
        } else {
            selected.insert(id)
        }
        session.myServices.save(selected)
    }

    private func load() async {
        if case .content = state { return }
        do {
            let services = try await session.client.streamingServices()
            // By name, because the server's order is its seeding order and
            // means nothing to a reader looking for one.
            let sorted = services.sorted {
                $0.name.lowercased() < $1.name.lowercased()
            }
            state = sorted.isEmpty ? .empty : .content(sorted)
        } catch {
            state = .failed(error)
        }
    }
}
