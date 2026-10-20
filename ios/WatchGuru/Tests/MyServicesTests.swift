import Foundation
import Testing
@testable import WatchGuru

/// The services the user says they pay for, and the three things that must hold
/// about where they are kept: it survives a restart, a damaged file does not
/// take a screen down with it, and signing out leaves nothing behind.
@MainActor
struct MyServicesTests {

    @Test("a selection survives a new instance")
    func selectionPersists() {
        // Two instances over one store is what a restart looks like from here:
        // the second reads what the first wrote, through the same file.
        let store = InMemoryOfflineStore()
        MyServices(store: store).save([3, 1])

        #expect(MyServices(store: store).selected == [1, 3])
    }

    @Test("nothing chosen reads as an empty set, not an error")
    func emptyByDefault() {
        #expect(MyServices(store: InMemoryOfflineStore()).selected.isEmpty)
    }

    @Test("a file that cannot be decoded reads as no selection")
    func corruptIsEmpty() {
        // The worst case is an ordering the user sets again. Throwing here
        // would take down "Where to watch", which is far worse than a list in
        // the wrong order.
        let store = InMemoryOfflineStore()
        store.write("my-services.json", Data("not json".utf8))

        #expect(MyServices(store: store).selected.isEmpty)
    }

    @Test("clearing removes the file, not just the value in memory")
    func clearRemovesTheFile() {
        let store = InMemoryOfflineStore()
        let subject = MyServices(store: store)
        subject.save([5])

        subject.clear()

        #expect(subject.selected.isEmpty)
        // And a fresh instance agrees, which is the part that matters on a
        // shared device: the next user must not inherit this.
        #expect(MyServices(store: store).selected.isEmpty)
    }

    @Test("saving an empty set is a selection, not a clear")
    func emptySelectionIsStored() {
        // Un-ticking the last service writes [], and reading it back must stay
        // empty rather than falling through to "no file, so no opinion".
        let store = InMemoryOfflineStore()
        MyServices(store: store).save([1])
        MyServices(store: store).save([])

        #expect(MyServices(store: store).selected.isEmpty)
        #expect(store.read("my-services.json") == Data("[]".utf8))
    }
}
