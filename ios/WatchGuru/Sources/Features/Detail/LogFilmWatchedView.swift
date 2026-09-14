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

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Button("Watched today") { log(Date.now) }
                    .buttonStyle(.borderedProminent)

                Button("Watched on a date…") {
                    chosenDay = Date.now
                    isPickingDate = true
                }
                .buttonStyle(.bordered)
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

    private func message(for outcome: TitleDetailModel.FilmLog) -> String {
        switch outcome {
        case .logged(let day):
            "Added to your history for \(day.formatted(date: .abbreviated, time: .omitted))."
        case .queued(let day):
            "Saved for \(day.formatted(date: .abbreviated, time: .omitted)). "
                + "It will be sent when you are back online."
        case .failed(let failure):
            failure.message
        }
    }

    private func isFailure(_ outcome: TitleDetailModel.FilmLog) -> Bool {
        if case .failed = outcome { return true }
        return false
    }
}
