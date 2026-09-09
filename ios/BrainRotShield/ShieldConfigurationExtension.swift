import ManagedSettings
import ManagedSettingsUI
import UIKit

/// Styles the screen iOS puts over a blocked app. Only these fields are ours:
/// a still image, two labels and two buttons. Custom views and animation are
/// not available here, which is why the animated brain arrives by notification.
class ShieldConfigurationExtension: ShieldConfigurationDataSource {

    override func configuration(shielding application: Application) -> ShieldConfiguration {
        cryingBrain()
    }

    override func configuration(
        shielding application: Application,
        in category: ActivityCategory
    ) -> ShieldConfiguration {
        cryingBrain()
    }

    override func configuration(shielding webDomain: WebDomain) -> ShieldConfiguration {
        cryingBrain()
    }

    override func configuration(
        shielding webDomain: WebDomain,
        in category: ActivityCategory
    ) -> ShieldConfiguration {
        cryingBrain()
    }

    private func cryingBrain() -> ShieldConfiguration {
        let settings = SharedStore.shared.settings

        return ShieldConfiguration(
            backgroundBlurStyle: .systemUltraThinMaterialDark,
            backgroundColor: UIColor(red: 0.086, green: 0.071, blue: 0.169, alpha: 0.92),
            icon: brainImage(),
            title: ShieldConfiguration.Label(
                text: "Your brain is crying",
                color: UIColor(red: 0.957, green: 0.933, blue: 1.0, alpha: 1.0)
            ),
            subtitle: ShieldConfiguration.Label(
                text: "You are past \(settings.limitMinutes) minutes in here.",
                color: UIColor(red: 0.725, green: 0.686, blue: 0.839, alpha: 1.0)
            ),
            primaryButtonLabel: ShieldConfiguration.Label(
                text: "I'll stop",
                color: UIColor(red: 0.235, green: 0.125, blue: 0.243, alpha: 1.0)
            ),
            primaryButtonBackgroundColor: UIColor(
                red: 0.969, green: 0.627, blue: 0.769, alpha: 1.0
            ),
            secondaryButtonLabel: ShieldConfiguration.Label(
                text: "\(settings.snoozeMinutes) more minutes",
                color: UIColor(red: 0.725, green: 0.686, blue: 0.839, alpha: 1.0)
            )
        )
    }

    private func brainImage() -> UIImage? {
        guard let url = Bundle.main.url(forResource: "crying_brain", withExtension: "png")
        else { return nil }
        return UIImage(contentsOfFile: url.path)
    }
}
