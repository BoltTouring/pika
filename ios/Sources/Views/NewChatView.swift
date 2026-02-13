import SwiftUI
import UIKit

struct NewChatView: View {
    let state: NewChatViewState
    let onCreateChat: @MainActor (String) -> Void
    @State private var npubInput = ""
    @State private var showScanner = false

    var body: some View {
        let peer = PeerKeyValidator.normalize(npubInput)
        let isValidPeer = PeerKeyValidator.isValidPeer(peer)
        let isLoading = state.isCreatingChat

        VStack(spacing: 12) {
            TextField("Peer npub", text: $npubInput)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .textFieldStyle(.roundedBorder)
                .disabled(isLoading)
                .accessibilityIdentifier(TestIds.newChatPeerNpub)

            HStack(spacing: 12) {
                Button("Scan QR") { showScanner = true }
                    .buttonStyle(.bordered)
                    .disabled(isLoading)
                    .accessibilityIdentifier(TestIds.newChatScanQr)

                Button("Paste") {
                    let raw = UIPasteboard.general.string ?? ""
                    npubInput = PeerKeyValidator.normalize(raw)
                }
                .buttonStyle(.bordered)
                .disabled(isLoading)
                .accessibilityIdentifier(TestIds.newChatPaste)

                Spacer()
            }

            if !peer.isEmpty && !isValidPeer {
                Text("Enter a valid npub1… or 64-char hex pubkey.")
                    .font(.footnote)
                    .foregroundStyle(.red)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            Button {
                onCreateChat(peer)
            } label: {
                if isLoading {
                    HStack(spacing: 8) {
                        ProgressView()
                            .tint(.white)
                        Text("Creating…")
                    }
                } else {
                    Text("Start Chat")
                }
            }
            .buttonStyle(.borderedProminent)
            .accessibilityIdentifier(TestIds.newChatStart)
            .disabled(!isValidPeer || isLoading)

            Spacer()
        }
        .padding(16)
        .navigationTitle("New Chat")
        .sheet(isPresented: $showScanner) {
            QrScannerSheet { scanned in
                npubInput = scanned
            }
        }
    }
}

#if DEBUG
#Preview("New Chat") {
    NavigationStack {
        NewChatView(
            state: NewChatViewState(isCreatingChat: false),
            onCreateChat: { _ in }
        )
    }
}

#Preview("New Chat - Creating") {
    NavigationStack {
        NewChatView(
            state: NewChatViewState(isCreatingChat: true),
            onCreateChat: { _ in }
        )
    }
}
#endif
