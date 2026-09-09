import DeviceActivity
import FamilyControls
import Foundation
import ManagedSettings
import UserNotifications

/// Drives the Screen Time APIs: authorization, the app selection, and the
/// device-activity schedule that fires once a chosen app passes its limit.
@MainActor
final class ScreenTimeController: ObservableObject {

    @Published var authorization: AuthorizationStatus
    @Published var notificationsAllowed = false
    @Published var lastError: String?

    @Published var selection: FamilyActivitySelection {
        didSet {
            SharedStore.shared.selection = selection
            if settings.monitoring { restart() }
        }
    }

    @Published var settings: BrainRotSettings {
        didSet {
            SharedStore.shared.settings = settings
            if settings.monitoring && settings != oldValue && oldValue.monitoring {
                restart()
            }
        }
    }

    private let center = DeviceActivityCenter()
    private let store = ManagedSettingsStore(named: BrainRot.storeName)

    init() {
        authorization = AuthorizationCenter.shared.authorizationStatus
        selection = SharedStore.shared.selection
        settings = SharedStore.shared.settings
    }

    var hasSelection: Bool {
        !selection.applicationTokens.isEmpty ||
            !selection.categoryTokens.isEmpty ||
            !selection.webDomainTokens.isEmpty
    }

    var selectionCount: Int {
        selection.applicationTokens.count +
            selection.categoryTokens.count +
            selection.webDomainTokens.count
    }

    func refresh() {
        authorization = AuthorizationCenter.shared.authorizationStatus
        Task {
            let current = await UNUserNotificationCenter.current().notificationSettings()
            notificationsAllowed = current.authorizationStatus == .authorized
        }
    }

    /// Screen Time access. `.individual` is an adult authorizing their own
    /// device; `.child` would need a parent in a Family Sharing group.
    func requestAuthorization() async {
        do {
            try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
            authorization = AuthorizationCenter.shared.authorizationStatus
        } catch {
            lastError = "Screen Time access was not granted: \(error.localizedDescription)"
        }
    }

    func requestNotifications() async {
        do {
            notificationsAllowed = try await UNUserNotificationCenter.current()
                .requestAuthorization(options: [.alert, .sound])
        } catch {
            lastError = error.localizedDescription
        }
    }

    func start() {
        guard hasSelection else {
            lastError = "Choose at least one app first."
            return
        }

        let event = DeviceActivityEvent(
            applications: selection.applicationTokens,
            categories: selection.categoryTokens,
            webDomains: selection.webDomainTokens,
            threshold: DateComponents(minute: settings.limitMinutes)
        )

        center.stopMonitoring()
        do {
            try center.startMonitoring(
                BrainRot.windowActivity,
                during: schedule,
                events: [BrainRot.limitEvent: event]
            )
            settings.monitoring = true
            lastError = nil
        } catch {
            settings.monitoring = false
            lastError = "Could not start monitoring: \(error.localizedDescription)"
        }
    }

    func stop() {
        center.stopMonitoring()
        store.clearAllSettings()
        settings.monitoring = false
    }

    /// Any change to the limit or the selection has to be re-registered; the
    /// schedule already running was built from the old values.
    private func restart() {
        guard hasSelection else {
            stop()
            return
        }
        store.clearAllSettings()
        start()
    }

    private var schedule: DeviceActivitySchedule {
        switch settings.resetWindow {
        case .daily:
            return DeviceActivitySchedule(
                intervalStart: DateComponents(hour: 0, minute: 0),
                intervalEnd: DateComponents(hour: 23, minute: 59),
                repeats: true
            )
        case .hourly:
            return DeviceActivitySchedule(
                intervalStart: DateComponents(minute: 0),
                intervalEnd: DateComponents(minute: 59),
                repeats: true
            )
        }
    }
}
