import DeviceActivity
import Foundation
import ManagedSettings
import UserNotifications

/// Runs in the background when a monitored threshold is crossed. This is the
/// only moment iOS gives us: there is no polling and no way to observe the
/// foreground app ourselves.
class DeviceActivityMonitorExtension: DeviceActivityMonitor {

    private let store = ManagedSettingsStore(named: BrainRot.storeName)

    override func intervalDidStart(for activity: DeviceActivityName) {
        super.intervalDidStart(for: activity)
        // A new window (new day, or new hour) starts with a clean slate.
        if activity == BrainRot.windowActivity {
            store.clearAllSettings()
        }
    }

    override func intervalDidEnd(for activity: DeviceActivityName) {
        super.intervalDidEnd(for: activity)
        store.clearAllSettings()
    }

    override func eventDidReachThreshold(
        _ event: DeviceActivityEvent.Name,
        activity: DeviceActivityName
    ) {
        super.eventDidReachThreshold(event, activity: activity)

        let settings = SharedStore.shared.settings
        if settings.blockApp {
            applyShield()
        }
        postCryingBrain(settings: settings, snoozeExpired: activity == BrainRot.snoozeActivity)

        // A snooze window is single-use.
        if activity == BrainRot.snoozeActivity {
            DeviceActivityCenter().stopMonitoring([BrainRot.snoozeActivity])
        }
    }

    private func applyShield() {
        let selection = SharedStore.shared.selection

        store.shield.applications =
            selection.applicationTokens.isEmpty ? nil : selection.applicationTokens
        store.shield.applicationCategories =
            selection.categoryTokens.isEmpty ? nil : .specific(selection.categoryTokens)
        store.shield.webDomains =
            selection.webDomainTokens.isEmpty ? nil : selection.webDomainTokens
    }

    /// The animated GIF rides along as a notification attachment, which is the
    /// one place on iOS where an app can show a moving image over whatever the
    /// user is doing. The shield itself only takes a still image.
    private func postCryingBrain(settings: BrainRotSettings, snoozeExpired: Bool) {
        let content = UNMutableNotificationContent()
        content.title = "Your brain is crying"
        content.body = snoozeExpired
            ? "That was the extra \(settings.snoozeMinutes) minutes."
            : "That is more than \(settings.limitMinutes) minutes in an app you said to watch."
        content.sound = .default
        if let attachment = gifAttachment() {
            content.attachments = [attachment]
        }

        let request = UNNotificationRequest(
            identifier: UUID().uuidString,
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request)
    }

    private func gifAttachment() -> UNNotificationAttachment? {
        guard let source = Bundle.main.url(forResource: "crying_brain", withExtension: "gif")
        else { return nil }

        // The system takes ownership of an attachment file, so hand it a copy.
        let destination = FileManager.default.temporaryDirectory
            .appendingPathComponent("crying_brain-\(UUID().uuidString).gif")
        do {
            try FileManager.default.copyItem(at: source, to: destination)
            return try UNNotificationAttachment(identifier: "cryingBrain", url: destination)
        } catch {
            return nil
        }
    }
}
