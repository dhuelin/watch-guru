import SwiftUI

/// A state that fills the screen: empty, or failed.
///
/// `ContentUnavailableView` is the native shape for this, so it inherits the
/// system's layout, spacing and Dynamic Type behaviour rather than
/// approximating them.
struct FullScreenMessage: View {
    let title: String
    let symbol: String
    var message: String?
    var actionTitle: String?
    var action: (() -> Void)?

    var body: some View {
        ContentUnavailableView {
            Label(title, systemImage: symbol)
        } description: {
            if let message {
                Text(message)
            }
        } actions: {
            if let actionTitle, let action {
                Button(actionTitle, action: action)
            }
        }
    }
}

/// Renders a failure as something the user can act on.
///
/// The distinction the backend works to preserve is honoured here: an
/// unreachable TMDB is not an outage, because the user's own library is still
/// current. Telling someone their data is gone when it is not is the worst
/// available answer.
struct FailureView: View {
    let failure: APIFailure
    var retry: (() -> Void)?

    var body: some View {
        FullScreenMessage(
            title: failure == .offline ? "Offline" : "Something went wrong",
            symbol: failure.symbol,
            message: failure.message,
            actionTitle: retry == nil ? nil : "Retry",
            action: retry
        )
    }
}
