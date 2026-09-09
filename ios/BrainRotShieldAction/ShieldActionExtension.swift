import DeviceActivity
import Foundation
import ManagedSettings

/// Handles the two buttons on the shield.
class ShieldActionExtension: ShieldActionDelegate {

    override func handle(
        action: ShieldAction,
        for application: ApplicationToken,
        completionHandler: @escaping (ShieldActionResponse) -> Void
    ) {
        respond(to: action, completionHandler: completionHandler)
    }

    override func handle(
        action: ShieldAction,
        for webDomain: WebDomainToken,
        completionHandler: @escaping (ShieldActionResponse) -> Void
    ) {
        respond(to: action, completionHandler: completionHandler)
    }

    override func handle(
        action: ShieldAction,
        for category: ActivityCategoryToken,
        completionHandler: @escaping (ShieldActionResponse) -> Void
    ) {
        respond(to: action, completionHandler: completionHandler)
    }

    private func respond(
        to action: ShieldAction,
        completionHandler: @escaping (ShieldActionResponse) -> Void
    ) {
        switch action {
        case .primaryButtonPressed:
            completionHandler(.close)
        case .secondaryButtonPressed:
            snooze()
            completionHandler(.defer)
        @unknown default:
            completionHandler(.close)
        }
    }

    /// Lifts the shield and starts a one-shot window that re-shields after the
    /// snooze worth of further usage. The snooze is measured in usage, not in
    /// wall-clock time, so putting the phone down does not burn it.
    private func snooze() {
        let settings = SharedStore.shared.settings
        let selection = SharedStore.shared.selection

        ManagedSettingsStore(named: BrainRot.storeName).clearAllSettings()

        let now = Calendar.current.dateComponents([.hour, .minute], from: Date())
        guard let hour = now.hour, let minute = now.minute else { return }

        // Too close to midnight for a usable window; the daily interval will
        // start over and re-arm the limit by itself.
        guard !(hour == 23 && minute >= 55) else { return }

        let event = DeviceActivityEvent(
            applications: selection.applicationTokens,
            categories: selection.categoryTokens,
            webDomains: selection.webDomainTokens,
            threshold: DateComponents(minute: settings.snoozeMinutes)
        )
        let schedule = DeviceActivitySchedule(
            intervalStart: DateComponents(hour: hour, minute: minute),
            intervalEnd: DateComponents(hour: 23, minute: 59),
            repeats: false
        )

        let center = DeviceActivityCenter()
        center.stopMonitoring([BrainRot.snoozeActivity])
        try? center.startMonitoring(
            BrainRot.snoozeActivity,
            during: schedule,
            events: [BrainRot.snoozeEvent: event]
        )
    }
}
