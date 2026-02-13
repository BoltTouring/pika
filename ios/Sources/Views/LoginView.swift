import SwiftUI

struct LoginView: View {
    let state: LoginViewState
    let onCreateAccount: @MainActor () -> Void
    let onLogin: @MainActor (String) -> Void
    @State private var nsecInput = ""

    var body: some View {
        let createBusy = state.creatingAccount
        let loginBusy = state.loggingIn
        let anyBusy = createBusy || loginBusy

        VStack(spacing: 16) {
            Text("Pika")
                .font(.largeTitle.weight(.semibold))

            Button {
                onCreateAccount()
            } label: {
                if createBusy {
                    ProgressView()
                        .tint(.white)
                } else {
                    Text("Create Account")
                }
            }
            .buttonStyle(.borderedProminent)
            .disabled(anyBusy)
            .accessibilityIdentifier(TestIds.loginCreateAccount)

            Divider().padding(.vertical, 8)

            TextField("nsec (mock)", text: $nsecInput)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .textFieldStyle(.roundedBorder)
                .disabled(anyBusy)
                .accessibilityIdentifier(TestIds.loginNsecInput)

            Button {
                onLogin(nsecInput)
            } label: {
                if loginBusy {
                    ProgressView()
                } else {
                    Text("Login")
                }
            }
            .buttonStyle(.bordered)
            .disabled(anyBusy)
            .accessibilityIdentifier(TestIds.loginSubmit)
        }
        .padding(20)
    }
}

#if DEBUG
#Preview("Login") {
    LoginView(
        state: LoginViewState(creatingAccount: false, loggingIn: false),
        onCreateAccount: {},
        onLogin: { _ in }
    )
}

#Preview("Login - Busy") {
    LoginView(
        state: LoginViewState(creatingAccount: false, loggingIn: true),
        onCreateAccount: {},
        onLogin: { _ in }
    )
}

#Preview("Login - Creating") {
    LoginView(
        state: LoginViewState(creatingAccount: true, loggingIn: false),
        onCreateAccount: {},
        onLogin: { _ in }
    )
}
#endif
