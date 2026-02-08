import XCTest

final class PikaUITests: XCTestCase {
    private func dismissPikaToastIfPresent(_ app: XCUIApplication, timeout: TimeInterval = 0.5) -> String? {
        let alert = app.alerts["Pika"]
        guard alert.waitForExistence(timeout: timeout) else { return nil }

        // Best-effort: capture the message for diagnostics.
        let msg = alert.staticTexts
            .allElementsBoundByIndex
            .map(\.label)
            .filter { !$0.isEmpty && $0 != "Pika" }
            .joined(separator: " ")

        let ok = alert.buttons["OK"]
        if ok.exists { ok.tap() }
        else { alert.buttons.element(boundBy: 0).tap() }
        return msg.isEmpty ? nil : msg
    }

    private func dismissAllPikaToasts(_ app: XCUIApplication, maxSeconds: TimeInterval = 10) -> [String] {
        let deadline = Date().addingTimeInterval(maxSeconds)
        var messages: [String] = []
        while Date() < deadline {
            if let msg = dismissPikaToastIfPresent(app, timeout: 0.25) {
                messages.append(msg)
                continue
            }
            Thread.sleep(forTimeInterval: 0.1)
        }
        return messages
    }

    func testCreateAccount_noteToSelf_sendMessage_and_logout() throws {
        let app = XCUIApplication()
        // Keep this test deterministic/offline.
        app.launchEnvironment["PIKA_UI_TEST_RESET"] = "1"
        app.launchEnvironment["PIKA_DISABLE_NETWORK"] = "1"
        app.launch()

        // If we land on Login, create an account; otherwise we may have restored a prior session.
        let createAccount = app.buttons.matching(identifier: "login_create_account").firstMatch
        if createAccount.waitForExistence(timeout: 2) {
            createAccount.tap()
            _ = dismissAllPikaToasts(app, maxSeconds: 5)
        }

        let chatsNavBar = app.navigationBars["Chats"]
        XCTAssertTrue(chatsNavBar.waitForExistence(timeout: 15))

        // Fetch our npub from the "My npub" alert (avoid clipboard access from UI tests).
        let myNpubBtn = app.buttons.matching(identifier: "chatlist_my_npub").firstMatch
        XCTAssertTrue(myNpubBtn.waitForExistence(timeout: 5))
        myNpubBtn.tap()

        let alert = app.alerts["My npub"]
        XCTAssertTrue(alert.waitForExistence(timeout: 5))
        // SwiftUI alert accessibility identifiers can be unreliable across iOS versions; match by label.
        let npubValue =
            alert.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "npub1")).firstMatch
        XCTAssertTrue(npubValue.waitForExistence(timeout: 5))
        let myNpub = npubValue.label
        XCTAssertTrue(myNpub.hasPrefix("npub1"), "Expected npub1..., got: \(myNpub)")

        // Close the alert.
        let close = alert.buttons["Close"]
        if close.exists { close.tap() }
        else { alert.buttons.element(boundBy: 0).tap() }

        // New chat.
        let newChat = app.buttons.matching(identifier: "chatlist_new_chat").firstMatch
        XCTAssertTrue(newChat.waitForExistence(timeout: 5))
        newChat.tap()

        XCTAssertTrue(app.navigationBars["New Chat"].waitForExistence(timeout: 10))

        let peer = app.descendants(matching: .any).matching(identifier: "newchat_peer_npub").firstMatch
        XCTAssertTrue(peer.waitForExistence(timeout: 10))
        peer.tap()
        peer.typeText(myNpub)

        let start = app.buttons.matching(identifier: "newchat_start").firstMatch
        XCTAssertTrue(start.waitForExistence(timeout: 5))
        start.tap()
        _ = dismissAllPikaToasts(app, maxSeconds: 5)

        // Send a message and ensure it appears.
        let msgField = app.textViews.matching(identifier: "chat_message_input").firstMatch
        let msgFieldFallback = app.textFields.matching(identifier: "chat_message_input").firstMatch
        let composer = msgField.exists ? msgField : msgFieldFallback
        XCTAssertTrue(composer.waitForExistence(timeout: 10))
        composer.tap()

        let msg = "hello from ios ui test"
        composer.typeText(msg)

        let send = app.buttons.matching(identifier: "chat_send").firstMatch
        XCTAssertTrue(send.waitForExistence(timeout: 5))
        send.tap()

        // Bubble text may not be visible if the keyboard overlaps; existence is enough.
        XCTAssertTrue(app.staticTexts[msg].waitForExistence(timeout: 10))

        // Back to chat list and logout.
        app.navigationBars.buttons.element(boundBy: 0).tap()
        XCTAssertTrue(chatsNavBar.waitForExistence(timeout: 10))

        let logout = app.buttons.matching(identifier: "chatlist_logout").firstMatch
        XCTAssertTrue(logout.waitForExistence(timeout: 5))
        logout.tap()

        XCTAssertTrue(app.staticTexts["Pika"].waitForExistence(timeout: 10))
    }

    func testE2E_deployedRustBot_pingPong() throws {
        // Opt-in test: run it explicitly via xcodebuild `-only-testing:`. This hits public relays,
        // so it is intentionally not part of the deterministic smoke suite.
        let botNpub =
            ProcessInfo.processInfo.environment["PIKA_UI_E2E_BOT_NPUB"] ??
            "npub1rtrxx9eyvag0ap3v73c4dvsqq5d2yxwe5d72qxrfpwe5svr96wuqed4p38"

        let relays =
            ProcessInfo.processInfo.environment["PIKA_UI_E2E_RELAYS"] ??
            "wss://relay.primal.net,wss://nos.lol,wss://relay.damus.io"
        let kpRelays =
            ProcessInfo.processInfo.environment["PIKA_UI_E2E_KP_RELAYS"] ??
            "wss://nostr-pub.wellorder.net,wss://nostr-01.yakihonne.com,wss://nostr-02.yakihonne.com,wss://relay.satlantis.io"

        let app = XCUIApplication()
        app.launchEnvironment["PIKA_UI_TEST_RESET"] = "1"
        app.launchEnvironment["PIKA_RELAY_URLS"] = relays
        app.launchEnvironment["PIKA_KEY_PACKAGE_RELAY_URLS"] = kpRelays
        app.launch()

        // Create account.
        let createAccount = app.buttons.matching(identifier: "login_create_account").firstMatch
        XCTAssertTrue(createAccount.waitForExistence(timeout: 5))
        createAccount.tap()
        _ = dismissAllPikaToasts(app, maxSeconds: 10)

        // Chat list.
        let chatsNavBar = app.navigationBars["Chats"]
        XCTAssertTrue(chatsNavBar.waitForExistence(timeout: 30))

        // Start chat with deployed bot.
        let newChat = app.buttons.matching(identifier: "chatlist_new_chat").firstMatch
        XCTAssertTrue(newChat.waitForExistence(timeout: 10))
        newChat.tap()

        XCTAssertTrue(app.navigationBars["New Chat"].waitForExistence(timeout: 15))

        let peer = app.descendants(matching: .any).matching(identifier: "newchat_peer_npub").firstMatch
        XCTAssertTrue(peer.waitForExistence(timeout: 10))
        peer.tap()
        peer.typeText(botNpub)

        let start = app.buttons.matching(identifier: "newchat_start").firstMatch
        XCTAssertTrue(start.waitForExistence(timeout: 10))
        start.tap()

        // Rust shows progress via toast; on iOS this is a modal alert.
        let toasts = dismissAllPikaToasts(app, maxSeconds: 30)
        for t in toasts {
            // If we hit an error toast, fail fast with the message.
            if t.lowercased().contains("failed") ||
                t.lowercased().contains("invalid peer key package") ||
                t.lowercased().contains("could not find peer key package")
            {
                XCTFail("E2E failed during chat creation: \(t)")
                return
            }
        }

        // Send ping.
        let msgField = app.textViews.matching(identifier: "chat_message_input").firstMatch
        let msgFieldFallback = app.textFields.matching(identifier: "chat_message_input").firstMatch
        let composer = msgField.exists ? msgField : msgFieldFallback
        XCTAssertTrue(composer.waitForExistence(timeout: 30))
        composer.tap()
        composer.typeText("ping")

        let send = app.buttons.matching(identifier: "chat_send").firstMatch
        XCTAssertTrue(send.waitForExistence(timeout: 10))
        send.tap()

        // Expect pong from the bot.
        XCTAssertTrue(app.staticTexts["pong"].waitForExistence(timeout: 180))
    }
}
