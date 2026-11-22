// A stand-in for BlazbudsKit, the Kotlin framework, as Kotlin/Native shows it to Swift. Written by hand from
// ui/src/iosMain/kotlin/com/blazify/buds/ui/Bridge.kt and MainViewController.kt: change it with them.
// Kotlin/Native's rules, which this follows:
// - an interface becomes a class-bound protocol, a top-level function a static method on "<File>Kt", an object
//   a class with a `shared` instance;
// - NSData stays Data, List<String> becomes [String], Boolean becomes Bool, (String) -> Unit an escaping closure;
// - names beginning with new, copy, alloc or init get "do" in front, and enum entries lose their capitals
//   (none of those cross over here, on purpose).
@_exported import Foundation
import UIKit

public protocol BluetoothBridge: AnyObject {
    func isOn() -> Bool
    func connectedEarbuds() -> [String]
    func connect(id: String, route: String, pipe: BudsPipe)
    func check(done: @escaping (String) -> Void)
    func watch(changed: @escaping () -> Void)
}

public protocol BudsWire: AnyObject {
    func send(data: Data)
    func close()
}

public final class BudsPipe {
    public init() {}
    public func opened(wire: BudsWire) {}
    public func failed(reason: String, busy: Bool) {}
    public func received(data: Data) {}
    public func ended() {}
    public func close() {}
}

public final class BudsLog {
    public static let shared = BudsLog()
    public func add(line: String) {}
}

public final class MainViewControllerKt {
    public static func MainViewController(bridge: BluetoothBridge) -> UIViewController { UIViewController() }
}
