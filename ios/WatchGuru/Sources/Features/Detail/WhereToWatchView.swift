import SwiftUI
import WatchGuruAPI

/// Where the user can actually watch this, in their own country.
///
/// Three states, not two. Offers are listed; no offers *that the server
/// confirmed* says so in as many words, because "not on anything here" is
/// useful and true; and no offers that nobody could confirm shows nothing at
/// all, because an empty list from an unreachable provider is not evidence of
/// anything. `checked` is what separates the last two.
///
/// The country is the one on the user's profile — the backend resolves it from
/// the token — which the caption says, because offers for the wrong country are
/// worse than no offers at all.
struct WhereToWatchView: View {

    let offers: [AvailabilityResponse]
    let checked: Bool

    private var groups: [WatchOffers.Group] { WatchOffers.group(offers) }

    var body: some View {
        if offers.isEmpty {
            if checked {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Where to watch").font(.headline)
                    Text("Not on any streaming service in your region.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Text("Offers for the country set in your profile.")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
        } else {
            VStack(alignment: .leading, spacing: 12) {
                Text("Where to watch").font(.headline)

                ForEach(groups) { group in
                    VStack(alignment: .leading, spacing: 8) {
                        Text(WatchOffers.label(for: group.offerType))
                            .font(.subheadline)
                            .foregroundStyle(.secondary)

                        LazyVGrid(
                            columns: [GridItem(.adaptive(minimum: 140), spacing: 8, alignment: .leading)],
                            alignment: .leading,
                            spacing: 8
                        ) {
                            ForEach(group.services, id: \.serviceId) { service in
                                ServiceChip(offer: service)
                            }
                        }
                    }
                }

                if let link = WatchOffers.link(offers) {
                    Link("See all watch options", destination: link)
                        .font(.subheadline)
                }

                Text("Offers for the country set in your profile.")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                // Availability is cached for a day. Without saying when it was
                // checked, a service that dropped the title this morning still
                // looks like a live answer.
                if let checkedAt = WatchOffers.checkedAt(offers) {
                    Text("Checked \(checkedAt.formatted(.relative(presentation: .named)))")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
                // Required alongside TMDB's own attribution: the availability
                // data is JustWatch's, and TMDB's terms say so.
                Text("Streaming availability data provided by JustWatch.")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

/// One service, named as well as pictured.
///
/// Not a button: the provider has no per-service deep link, so a chip that
/// looked tappable would open the same web page whichever service was pressed.
/// The single link below the groups is the honest version of that.
private struct ServiceChip: View {
    let offer: AvailabilityResponse

    var body: some View {
        HStack(spacing: 8) {
            ServiceLogo(url: offer.logoUrl.flatMap(URL.init(string:)), name: offer.serviceName)
            Text(offer.serviceName)
                .font(.subheadline)
                .lineLimit(1)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        .background(.quaternary, in: RoundedRectangle(cornerRadius: 8))
        // One element for the pair, so VoiceOver says "Netflix" rather than
        // stopping on a decorative image first.
        .accessibilityElement(children: .combine)
        .accessibilityLabel(offer.serviceName)
    }
}

/// The service's logo, or its initial where there isn't one.
private struct ServiceLogo: View {
    let url: URL?
    let name: String

    var body: some View {
        RoundedRectangle(cornerRadius: 6)
            .fill(.background)
            .frame(width: 28, height: 28)
            .overlay {
                if let url {
                    AsyncImage(url: url) { image in
                        image.resizable().scaledToFit()
                    } placeholder: {
                        Color.clear
                    }
                    .padding(2)
                } else {
                    Text(Self.initial(of: name))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 6))
            // The name sits right beside it; describing the logo too would
            // make VoiceOver read the service twice.
            .accessibilityHidden(true)
    }

    /// `nonisolated` for the same reason `PosterView.initials` is: pure, and
    /// otherwise main-actor isolated for no reason because the enclosing type
    /// is a `View`.
    nonisolated static func initial(of name: String) -> String {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let first = trimmed.first else { return "?" }
        return String(first).uppercased()
    }
}
