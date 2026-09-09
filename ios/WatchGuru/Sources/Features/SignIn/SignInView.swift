import AuthenticationServices
import SwiftUI

/// The screen shown when there is no usable token.
///
/// Deliberately sparse. There is nothing to configure and no account to create
/// — the first valid token provisions one — so the only thing to do here is
/// sign in.
struct SignInView: View {

    let model: SignInModel

    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            Image(systemName: "play.tv")
                .font(.system(size: 56))
                .foregroundStyle(.tint)

            VStack(spacing: 8) {
                Text("Watch Guru")
                    .font(.largeTitle.bold())
                Text("Know what you've watched and where you left off.")
                    .font(.body)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }

            Spacer()

            SignInWithAppleButton(.signIn) { request in
                // .fullName because Apple returns the name only in this first
                // authorisation and never again; .email so the backend can link
                // an Apple account to a Google one for the same person.
                request.requestedScopes = [.fullName, .email]
            } onCompletion: { result in
                Task { await model.complete(result) }
            }
            .signInWithAppleButtonStyle(.black)
            .frame(height: 50)
            .disabled(model.state == .signingIn)
            .accessibilityLabel("Sign in with Apple")

            if case .failed(let message) = model.state {
                Text(message)
                    .font(.footnote)
                    .foregroundStyle(.red)
                    .multilineTextAlignment(.center)
            }

            Text("This product uses the TMDB API but is not endorsed or certified by TMDB.")
                .font(.caption2)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(32)
    }
}
