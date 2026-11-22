// A stand-in for the little of UIKit the iPhone app uses, with the real signatures, so its Swift can be
// type-checked on Linux. Bodies do nothing.
@_exported import Foundation

open class UIViewController: NSObject {
    public override init() {}
}

open class UIDevice: NSObject {
    public static var current: UIDevice { UIDevice() }
    open var systemVersion: String { "" }
    open var model: String { "" }
}
