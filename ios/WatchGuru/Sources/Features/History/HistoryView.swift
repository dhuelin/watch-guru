import SwiftUI
import WatchGuruAPI

/// The viewing history: a timeline by day, and the place mistakes get
/// corrected.
struct HistoryView: View {

    @Environment(Session.self) private var session
    @State private var model: HistoryModel?
    @State private var editing: WatchEventResponse?

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
                await created.loadServices()
            }
        }
        .sheet(item: $editing) { event in
            if let model {
                NavigationStack {
                    EditEventView(event: event, services: model.services) { watchedAt, serviceId in
                        Task { await model.edit(event, watchedAt: watchedAt, serviceId: serviceId) }
                        editing = nil
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func content(_ model: HistoryModel) -> some View {
        @Bindable var model = model

        if model.state.value != nil {
            List {
                Section {
                    Filters(model: model)
                }

                ForEach(model.days) { day in
                    Section {
                        ForEach(day.events, id: \.id) { event in
                            Button {
                                editing = event
                            } label: {
                                HistoryRow(event: event)
                            }
                            .buttonStyle(.plain)
                            .swipeActions(edge: .trailing) {
                                Button(role: .destructive) {
                                    Task { await model.delete(event) }
                                } label: {
                                    Label("Delete", systemImage: "trash")
                                }
                            }
                        }
                    } header: {
                        // The header says how much the day holds, so a screen
                        // reader hears "13 September, 3 viewings" rather than a
                        // date and then a silence to count through.
                        Text(day.date.formatted(.dateTime.day().month(.wide).year()))
                            .accessibilityLabel(
                                "\(day.date.formatted(.dateTime.day().month(.wide).year())), "
                                    + HistoryDays.spokenSummary(day))
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
                VStack {
                    Filters(model: model)
                    ContentUnavailableView(
                        "Nothing here",
                        systemImage: "clock.arrow.circlepath",
                        description: Text(model.query.isEmpty
                            ? "Marking an episode will show it here."
                            : "No viewing matches that search.")
                    )
                }
            }
        }
    }
}

/// One filter row, above everything it scopes.
///
/// Search, then what kind of thing, then where it was watched — the same order
/// somebody would say them.
private struct Filters: View {

    @Bindable var model: HistoryModel

    @State private var isPickingRange = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            TextField("Search your history", text: $model.query)
                .textFieldStyle(.roundedBorder)
                .autocorrectionDisabled()
                // On submit rather than on every keystroke: each change is a
                // request, and one per letter would be a request per letter.
                .onSubmit { Task { await model.load() } }

            Picker("Show", selection: $model.type) {
                ForEach(HistoryType.allCases) { type in
                    Text(type.label).tag(type)
                }
            }
            .pickerStyle(.segmented)
            .onChange(of: model.type) { Task { await model.load() } }

            // "What did I watch last October" is the question, so the span of
            // days is a filter like any other rather than something buried in
            // a menu.
            HStack {
                Button {
                    isPickingRange = true
                } label: {
                    Label(model.range.label, systemImage: "calendar")
                }
                .buttonStyle(.bordered)

                if !model.range.isAnyTime {
                    Button("Clear dates") {
                        model.range = .anyTime
                        Task { await model.load() }
                    }
                    .buttonStyle(.borderless)
                }
            }

            if !model.services.isEmpty {
                Picker("Service", selection: $model.serviceId) {
                    Text("Any service").tag(Int64?.none)
                    ForEach(model.services, id: \.id) { service in
                        Text(service.name).tag(Int64?.some(service.id))
                    }
                }
                .onChange(of: model.serviceId) { Task { await model.load() } }
            }
        }
        .padding(.vertical, 4)
        .sheet(isPresented: $isPickingRange) {
            DateRangeSheet(range: model.range) { picked in
                isPickingRange = false
                guard picked != model.range else { return }
                model.range = picked
                Task { await model.load() }
            } cancel: {
                isPickingRange = false
            }
        }
    }
}

/// Picking the span of days, both bounds inclusive.
///
/// Two date pickers with switches rather than a range control, because SwiftUI
/// has no range picker and because either bound alone is a real question:
/// "since March" and "up to March" are both things people ask of a history.
/// The switches are what make "no bound that way" expressible at all — a bare
/// DatePicker always holds a date.
private struct DateRangeSheet: View {

    let apply: (DateRange) -> Void
    let cancel: () -> Void

    @State private var hasFrom: Bool
    @State private var hasTo: Bool
    @State private var from: Date
    @State private var to: Date

    init(range: DateRange, apply: @escaping (DateRange) -> Void, cancel: @escaping () -> Void) {
        self.apply = apply
        self.cancel = cancel
        _hasFrom = State(initialValue: range.from != nil)
        _hasTo = State(initialValue: range.to != nil)
        _from = State(initialValue: range.from ?? Date.now)
        _to = State(initialValue: range.to ?? Date.now)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Toggle("From a date", isOn: $hasFrom)
                    if hasFrom {
                        DatePicker("From", selection: $from, displayedComponents: .date)
                    }
                }
                Section {
                    Toggle("Up to a date", isOn: $hasTo)
                    if hasTo {
                        // Never before the start: a range that ends before it
                        // begins renders as an empty history, which reads as
                        // "you watched nothing" rather than as a bad filter.
                        DatePicker(
                            "Up to",
                            selection: $to,
                            in: (hasFrom ? from : Date.distantPast)...,
                            displayedComponents: .date)
                    }
                }
            }
            .navigationTitle("Dates")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", action: cancel)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") {
                        apply(DateRange(from: hasFrom ? from : nil, to: hasTo ? to : nil))
                    }
                }
            }
        }
        .presentationDetents([.medium])
    }
}

/// Correcting one entry: when it was watched, and where.
///
/// A date rather than a date and a time. Nobody remembers that they started a
/// film at 20:47, and asking for a precision the user does not have invites a
/// wrong answer; the time of day is kept from the original event so a
/// correction to the date changes only the date.
private struct EditEventView: View {

    let event: WatchEventResponse
    let services: [StreamingServiceResponse]
    let onSave: (Date?, Int64?) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var date: Date
    @State private var serviceId: Int64?

    init(
        event: WatchEventResponse,
        services: [StreamingServiceResponse],
        onSave: @escaping (Date?, Int64?) -> Void
    ) {
        self.event = event
        self.services = services
        self.onSave = onSave
        _date = State(initialValue: event.watchedAt)
        _serviceId = State(initialValue: event.streamingServiceId)
    }

    var body: some View {
        Form {
            Section {
                Text(event.primaryTitle).font(.headline)
                if let subtitle = episodeLine {
                    Text(subtitle).font(.subheadline).foregroundStyle(.secondary)
                }
            }

            Section {
                DatePicker("Watched on", selection: $date, displayedComponents: .date)
            }

            if !services.isEmpty {
                Section {
                    Picker("Watched where", selection: $serviceId) {
                        Text("Not recorded").tag(Int64?.none)
                        ForEach(services, id: \.id) { service in
                            Text(service.name).tag(Int64?.some(service.id))
                        }
                    }
                }
            }
        }
        .navigationTitle("Correct this entry")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button("Save") {
                    // Only what changed is sent: nil leaves a field alone
                    // server-side, so an untouched service is not resent and
                    // cannot be got wrong on the way.
                    onSave(movedDate, serviceId == event.streamingServiceId ? nil : serviceId)
                }
                .disabled(movedDate == nil && serviceId == event.streamingServiceId)
            }
            ToolbarItem(placement: .cancellationAction) {
                Button("Cancel") { dismiss() }
            }
        }
    }

    /// The new instant, keeping the original time of day, or nil if the day did
    /// not change.
    private var movedDate: Date? {
        let calendar = Calendar.current
        guard !calendar.isDate(date, inSameDayAs: event.watchedAt) else { return nil }

        let time = calendar.dateComponents([.hour, .minute, .second], from: event.watchedAt)
        return calendar.date(
            bySettingHour: time.hour ?? 12,
            minute: time.minute ?? 0,
            second: time.second ?? 0,
            of: date)
    }

    private var episodeLine: String? {
        let parts = [event.episodeCode, event.episodeName].compactMap { $0 }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
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
