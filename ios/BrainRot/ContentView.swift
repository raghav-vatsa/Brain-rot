import FamilyControls
import SwiftUI

struct ContentView: View {

    @StateObject private var controller = ScreenTimeController()
    @State private var showPicker = false
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        NavigationStack {
            Form {
                headerSection
                if controller.authorization != .approved {
                    authorizationSection
                } else {
                    appsSection
                    limitSection
                    reactionSection
                    monitoringSection
                }
                if let error = controller.lastError {
                    Section {
                        Text(error).foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle("Brain Rot")
            .familyActivityPicker(isPresented: $showPicker, selection: $controller.selection)
            .onChange(of: scenePhase) { _ in controller.refresh() }
            .onAppear { controller.refresh() }
        }
    }

    private var headerSection: some View {
        Section {
            HStack(spacing: 16) {
                AnimatedGIF(resource: "crying_brain")
                    .frame(width: 96, height: 96)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                VStack(alignment: .leading, spacing: 4) {
                    Text("Pick your poison")
                        .font(.headline)
                    Text("Pass your limit in a chosen app and this brain turns up on top of it.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }
            .padding(.vertical, 4)
        }
    }

    private var authorizationSection: some View {
        Section("Screen Time access") {
            Text("iOS only reports app usage through Screen Time, and only after you allow it. Nothing about your usage leaves the phone.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Button("Allow Screen Time access") {
                Task { await controller.requestAuthorization() }
            }
            if controller.authorization == .denied {
                Text("Denied. Turn it back on in Settings → Screen Time → Apps with Screen Time Access.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var appsSection: some View {
        Section("Apps to watch") {
            Button {
                showPicker = true
            } label: {
                HStack {
                    Text("Choose apps")
                    Spacer()
                    Text(controller.hasSelection ? "\(controller.selectionCount) selected" : "None")
                        .foregroundStyle(.secondary)
                }
            }
            Text("The picker is Apple's own, and it is the only thing that ever sees the list. This app receives anonymous tokens, never the names of the apps you chose.")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }

    private var limitSection: some View {
        Section("Time limit") {
            Picker("Limit", selection: $controller.settings.limitMinutes) {
                ForEach(BrainRotSettings.limitChoices, id: \.self) { minutes in
                    Text("\(minutes) min").tag(minutes)
                }
            }
            Picker("Counter resets", selection: $controller.settings.resetWindow) {
                ForEach(ResetWindow.allCases) { window in
                    Text(window.label).tag(window)
                }
            }
            Text("iOS counts usage per window, not per sitting: with a daily reset, \(controller.settings.limitMinutes) minutes is the total for the whole day.")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }

    private var reactionSection: some View {
        Section("When you pass it") {
            Toggle("Block the app", isOn: $controller.settings.blockApp)
            Picker("Snooze", selection: $controller.settings.snoozeMinutes) {
                ForEach(BrainRotSettings.snoozeChoices, id: \.self) { minutes in
                    Text("\(minutes) min").tag(minutes)
                }
            }
            if !controller.notificationsAllowed {
                Button("Allow notifications") {
                    Task { await controller.requestNotifications() }
                }
            }
            Text(controller.settings.blockApp
                 ? "The app is covered by a crying-brain screen until you close it or snooze."
                 : "You get a notification with the crying brain, and the app keeps working.")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }

    private var monitoringSection: some View {
        Section {
            if controller.settings.monitoring {
                Button("Stop watching", role: .destructive) { controller.stop() }
            } else {
                Button("Start watching") { controller.start() }
                    .disabled(!controller.hasSelection)
            }
        } footer: {
            Text(controller.settings.monitoring
                 ? "Watching \(controller.selectionCount) selection(s)."
                 : "Nothing is being watched right now.")
        }
    }
}

#Preview {
    ContentView()
}
