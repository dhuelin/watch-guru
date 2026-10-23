import SwiftUI

/// Placeholder rows shown while a list loads for the first time.
///
/// Shaped like the rows they stand in for, so the screen does not jump when
/// the real ones arrive, and so a slow first load reads as "your list is on its
/// way" rather than a spinner in the middle of nothing. Only for a first load:
/// a refresh keeps the rows the user is already looking at.
struct SkeletonList: View {
    var rows = 6
    /// Off for lists whose rows carry no artwork, such as History.
    var showsPoster = true
    var style: Style = .plain

    enum Style { case plain, insetGrouped }

    var body: some View {
        list
            .scrollDisabled(true)
            .allowsHitTesting(false)
            // One element for VoiceOver, not six unlabelled shapes.
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text("Loading"))
    }

    @ViewBuilder
    private var list: some View {
        let content = List(0..<rows, id: \.self) { _ in
            SkeletonRow(showsPoster: showsPoster)
        }
        switch style {
        case .plain: content.listStyle(.plain)
        case .insetGrouped: content.listStyle(.insetGrouped)
        }
    }
}

private struct SkeletonRow: View {
    let showsPoster: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var dimmed = false

    var body: some View {
        HStack(spacing: 12) {
            if showsPoster {
                RoundedRectangle(cornerRadius: 8)
                    .aspectRatio(Artwork.posterAspectRatio, contentMode: .fit)
                    .frame(width: 48)
            }
            VStack(alignment: .leading, spacing: 8) {
                RoundedRectangle(cornerRadius: 4)
                    .frame(maxWidth: 180, maxHeight: 14)
                RoundedRectangle(cornerRadius: 4)
                    .frame(maxWidth: 110, maxHeight: 10)
            }
            Spacer(minLength: 0)
        }
        .foregroundStyle(.quaternary)
        .padding(.vertical, 4)
        .opacity(dimmed ? 0.5 : 1)
        .onAppear {
            // A slow pulse says "still working" without the motion that
            // Reduce Motion asks to be spared.
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) {
                dimmed = true
            }
        }
    }
}
