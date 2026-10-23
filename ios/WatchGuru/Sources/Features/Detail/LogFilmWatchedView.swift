import SwiftUI
import WatchGuruAPI

/// Logging a film, today or on a day the user remembers.
///
/// A film has no episode to tick, so until now one could reach the library but
/// never the history — and the history is what the statistics, the streak and
/// "what did I watch last October" are all made of.
///
/// Two actions rather than one control with a date in it. Marking something as
/// you finish it is the common case and stays one tap; naming a past date is
/// the rarer one and is allowed to cost a sheet.
struct LogFilmWatchedView: View {

    let isMarking: Bool
    let outcome: TitleDetailModel.FilmLog?
    let log: (Date) -> Void
    let dismissOutcome: () -> Void

    @State private var isPickingDate = false
    @State private var chosenDay = Date.now
    /// True for a few seconds after "Watched today", so a double tap is one
    /// viewing rather than a viewing and a rewatch. A film log is not
    /// idempotent: the server files a second one as watching it again.
    @State private var coolingDown = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Side by side while they fit, stacked when they do not. At
            // accessibility sizes the row broke words in half ("Watche / d
            // today") and ran off the screen; ViewThatFits measures each
            // layout at its ideal size, so the stack is chosen before any
            // label has to wrap.
            ViewThatFits(in: .horizontal) {
                HStack { buttons }
                VStack(alignment: .leading) { buttons }
            }
            .disabled(isMarking)

            // Said plainly, and differently for queued: on a train the honest
            // answer is that the record is still on the phone.
            if let outcome {
                Text(message(for: outcome))
                    .font(.footnote)
                    .foregroundStyle(isFailure(outcome) ? AnyShapeStyle(.red) : AnyShapeStyle(.secondary))
            }
        }
        .sheet(isPresented: $isPickingDate) {
            NavigationStack {
                DatePicker(
                    "Watched on",
                    selection: $chosenDay,
                    // Nobody has watched anything tomorrow. Refusing the future
                    // here is kinder than a server error after the fact, and it
                    // is the only bound worth having: films get rewatched
                    // decades after they came out.
                    in: ...Date.now,
                    displayedComponents: .date
                )
                .datePickerStyle(.graphical)
                .padding()
                .navigationTitle("When did you watch it?")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { isPickingDate = false }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Done") {
                            isPickingDate = false
                            dismissOutcome()
                            log(chosenDay)
                        }
                    }
                }
            }
            .presentationDetents([.medium, .large])
        }
    }

    @ViewBuilder
    private var buttons: some View {
        Button {
            log(Date.now)
            coolingDown = true
            Task {
                try? await Task.sleep(for: .seconds(3))
                coolingDown = false
            }
        } label: {
            Text("Watched today").frame(maxWidth: .infinity)
        }
        .prominentAction()
        .disabled(coolingDown)

        Button {
            chosenDay = Date.now
            isPickingDate = true
        } label: {
            Text("Watched on a date…").frame(maxWidth: .infinity)
        }
        .buttonStyle(.bordered)
    }

    private func message(for outcome: TitleDetailModel.FilmLog) -> String {
        switch outcome {
        // "Today" rather than today's date: the date reads as if the user had
        // picked one, when they pressed the button that does not ask.
        case .logged(let day) where Calendar.current.isDateInToday(day):
            String(localized: "Added to your history for today.")
        case .queued(let day) where Calendar.current.isDateInToday(day):
            String(localized: "Saved for today. It will be sent when you are back online.")
        case .logged(let day):
            String(localized:
                "Added to your history for \(day.formatted(date: .abbreviated, time: .omitted)).")
        case .queued(let day):
            // One key, not two joined: a translation has to be able to put the
            // date where its language puts it, and a sentence split in half
            // cannot be reordered.
            String(localized:
                "Saved for \(day.formatted(date: .abbreviated, time: .omitted)). It will be sent when you are back online.")
        case .failed(let failure):
            failure.message
        }
    }

    private func isFailure(_ outcome: TitleDetailModel.FilmLog) -> Bool {
        if case .failed = outcome { return true }
        return false
    }
}
