// A stand-in for the parts of ExternalAccessory the iPhone app uses, with Apple's signatures, so its Swift can be
// type-checked on Linux. Bodies do nothing.
@_exported import Foundation

open class EAAccessory: NSObject {
    open var isConnected: Bool { false }
    open var connectionID: Int { 0 }
    open var manufacturer: String { "" }
    open var name: String { "" }
    open var modelNumber: String { "" }
    open var serialNumber: String { "" }
    open var firmwareRevision: String { "" }
    open var hardwareRevision: String { "" }
    open var protocolStrings: [String] { [] }
}

open class EAAccessoryManager: NSObject {
    open class func shared() -> EAAccessoryManager { EAAccessoryManager() }
    open func registerForLocalNotifications() {}
    open func unregisterForLocalNotifications() {}
    open var connectedAccessories: [EAAccessory] { [] }
}

open class EASession: NSObject {
    public init?(accessory: EAAccessory, forProtocol protocolString: String) {}
    open var accessory: EAAccessory? { nil }
    open var protocolString: String? { nil }
    open var inputStream: InputStream? { nil }
    open var outputStream: OutputStream? { nil }
}

extension Notification.Name {
    public static let EAAccessoryDidConnect = Notification.Name("EAAccessoryDidConnectNotification")
    public static let EAAccessoryDidDisconnect = Notification.Name("EAAccessoryDidDisconnectNotification")
}
