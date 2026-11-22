import SwiftUI
import UIKit
import BlazbudsKit

/// The iPhone app: one window showing the shared Compose screens, with Bluetooth done here in Swift.
@main
struct BlazbudsApp: App {
    var body: some Scene {
        WindowGroup {
            // The screens draw under the status bar and the home bar themselves, as they do on Android.
            AppScreens().ignoresSafeArea()
        }
    }
}

/// Hosts the Kotlin side's view controller.
struct AppScreens: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(bridge: Bluetooth.shared)
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
