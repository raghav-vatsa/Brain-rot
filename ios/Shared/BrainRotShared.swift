import DeviceActivity
import FamilyControls
import Foundation
import ManagedSettings

/// Names shared by the app and its three extensions. They have to agree
/// exactly: the extensions are separate processes that find each other only
/// through these identifiers and the app group.
enum BrainRot {
    /// Must match the App Group in every .entitlements file in this project.
    static let appGroup = "group.com.brainrot.detector"

    static let storeName = ManagedSettingsStore.Name("brainRot")
    static let windowActivity = DeviceActivityName("brainRot.window")
    static let limitEvent = DeviceActivityEventName("brainRot.limit")
    static let snoozeActivity = DeviceActivityName("brainRot.snooze")
    static let snoozeEvent = DeviceActivityEventName("brainRot.snooze")
}

/// How often the usage counter starts over. iOS counts usage per scheduled
/// interval, so this is the interval rather than a per-sitting timer.
enum ResetWindow: String, Codable, CaseIterable, Identifiable {
    case daily
    case hourly

    var id: String { rawValue }

    var label: String {
        switch self {
        case .daily: return "Each day"
        case .hourly: return "Each hour"
        }
    }
}

struct BrainRotSettings: Codable, Equatable {
    var limitMinutes: Int = 10
    var snoozeMinutes: Int = 5
    /// True: shield the app when the limit is passed. False: only notify.
    var blockApp: Bool = true
    var monitoring: Bool = false
    var resetWindow: ResetWindow = .daily

    static let limitChoices = [1, 2, 5, 10, 15, 20, 30, 45, 60, 90, 120]
    static let snoozeChoices = [1, 2, 5, 10, 15, 30]
}

/// The one piece of state the app and the extensions both touch, kept in the
/// App Group container.
final class SharedStore {

    static let shared = SharedStore()

    private let defaults: UserDefaults
    private let settingsKey = "settings"
    private let selectionKey = "selection"

    private init() {
        defaults = UserDefaults(suiteName: BrainRot.appGroup) ?? .standard
    }

    var settings: BrainRotSettings {
        get {
            guard let data = defaults.data(forKey: settingsKey),
                  let decoded = try? JSONDecoder().decode(BrainRotSettings.self, from: data)
            else { return BrainRotSettings() }
            return decoded
        }
        set {
            guard let data = try? JSONEncoder().encode(newValue) else { return }
            defaults.set(data, forKey: settingsKey)
        }
    }

    /// The chosen apps, as opaque tokens. iOS never tells us their names.
    var selection: FamilyActivitySelection {
        get {
            guard let data = defaults.data(forKey: selectionKey),
                  let decoded = try? JSONDecoder().decode(FamilyActivitySelection.self, from: data)
            else { return FamilyActivitySelection() }
            return decoded
        }
        set {
            guard let data = try? JSONEncoder().encode(newValue) else { return }
            defaults.set(data, forKey: selectionKey)
        }
    }
}
