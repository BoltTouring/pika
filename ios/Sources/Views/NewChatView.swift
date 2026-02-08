import SwiftUI

struct NewChatView: View {
    let manager: AppManager
    @State private var npubInput = ""

    var body: some View {
        let peer = npubInput.trimmingCharacters(in: .whitespacesAndNewlines)
        let isValidPeer = PeerKeyValidator.isValidPeer(peer)

        VStack(spacing: 12) {
            TextField("Peer npub", text: $npubInput)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .textFieldStyle(.roundedBorder)
                .accessibilityIdentifier(TestIds.newChatPeerNpub)

            if !peer.isEmpty && !isValidPeer {
                Text("Enter a valid npub1… or 64-char hex pubkey.")
                    .font(.footnote)
                    .foregroundStyle(.red)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            Button("Start Chat") {
                manager.dispatch(.createChat(peerNpub: peer))
            }
            .buttonStyle(.borderedProminent)
            .accessibilityIdentifier(TestIds.newChatStart)
            .disabled(!isValidPeer)

            Spacer()
        }
        .padding(16)
        .navigationTitle("New Chat")
    }
}
