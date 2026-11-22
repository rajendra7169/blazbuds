// A stand-in for the parts of CoreBluetooth the iPhone app uses, with Apple's signatures as of iOS 17, so its Swift
// can be type-checked on Linux. Bodies do nothing. Optional delegate methods can't be declared on Linux (they need
// Objective-C), so a misspelled one is not caught here: compare those with Apple's documentation by eye.
@_exported import Foundation

public enum CBManagerState: Int {
    case unknown = 0, resetting, unsupported, unauthorized, poweredOff, poweredOn
}

public enum CBManagerAuthorization: Int {
    case notDetermined = 0, restricted, denied, allowedAlways
}

open class CBManager: NSObject {
    open var state: CBManagerState { .unknown }
    open class var authorization: CBManagerAuthorization { .notDetermined }
}

public enum CBPeripheralState: Int {
    case disconnected = 0, connecting, connected, disconnecting
}

public enum CBCharacteristicWriteType: Int {
    case withResponse = 0, withoutResponse
}

public struct CBCharacteristicProperties: OptionSet {
    public let rawValue: UInt
    public init(rawValue: UInt) { self.rawValue = rawValue }
    public static let broadcast = CBCharacteristicProperties(rawValue: 0x01)
    public static let read = CBCharacteristicProperties(rawValue: 0x02)
    public static let writeWithoutResponse = CBCharacteristicProperties(rawValue: 0x04)
    public static let write = CBCharacteristicProperties(rawValue: 0x08)
    public static let notify = CBCharacteristicProperties(rawValue: 0x10)
    public static let indicate = CBCharacteristicProperties(rawValue: 0x20)
}

public enum CBConnectionEvent: Int {
    case peerDisconnected = 0, peerConnected
}

public struct CBConnectionEventMatchingOption: Hashable, RawRepresentable {
    public let rawValue: String
    public init(rawValue: String) { self.rawValue = rawValue }
    public static let serviceUUIDs = CBConnectionEventMatchingOption(rawValue: "kCBConnectionEventMatchingOptionServiceUUIDs")
    public static let peripheralUUIDs = CBConnectionEventMatchingOption(rawValue: "kCBConnectionEventMatchingOptionPeripheralUUIDs")
}

public let CBCentralManagerOptionShowPowerAlertKey: String = "kCBInitOptionShowPowerAlert"
public let CBCentralManagerOptionRestoreIdentifierKey: String = "kCBRestoreIdentifierKey"
public let CBCentralManagerRestoredStatePeripheralsKey: String = "kCBRestoredPeripherals"
public let CBCentralManagerScanOptionAllowDuplicatesKey: String = "kCBScanOptionAllowDuplicates"
public let CBAdvertisementDataLocalNameKey: String = "kCBAdvDataLocalName"
public let CBAdvertisementDataServiceUUIDsKey: String = "kCBAdvDataServiceUUIDs"

open class CBUUID: NSObject {
    public init(string theString: String) {}
    public init(data theData: Data) {}
    open var uuidString: String { "" }
    open var data: Data { Data() }
}

open class CBAttribute: NSObject {
    open var uuid: CBUUID { CBUUID(string: "") }
}

open class CBService: CBAttribute {
    weak open var peripheral: CBPeripheral? { nil }
    open var isPrimary: Bool { true }
    open var characteristics: [CBCharacteristic]? { nil }
}

open class CBCharacteristic: CBAttribute {
    weak open var service: CBService? { nil }
    open var properties: CBCharacteristicProperties { [] }
    open var value: Data? { nil }
    open var isNotifying: Bool { false }
}

open class CBPeer: NSObject {
    open var identifier: UUID { UUID() }
}

public protocol CBPeripheralDelegate: NSObjectProtocol {}

open class CBPeripheral: CBPeer {
    weak open var delegate: CBPeripheralDelegate?
    open var name: String? { nil }
    open var state: CBPeripheralState { .disconnected }
    open var services: [CBService]? { nil }
    open var canSendWriteWithoutResponse: Bool { true }
    open func discoverServices(_ serviceUUIDs: [CBUUID]?) {}
    open func discoverCharacteristics(_ characteristicUUIDs: [CBUUID]?, for service: CBService) {}
    open func readValue(for characteristic: CBCharacteristic) {}
    open func maximumWriteValueLength(for type: CBCharacteristicWriteType) -> Int { 20 }
    open func writeValue(_ data: Data, for characteristic: CBCharacteristic, type: CBCharacteristicWriteType) {}
    open func setNotifyValue(_ enabled: Bool, for characteristic: CBCharacteristic) {}
}

public protocol CBCentralManagerDelegate: NSObjectProtocol {
    func centralManagerDidUpdateState(_ central: CBCentralManager)
}

open class CBCentralManager: CBManager {
    weak open var delegate: CBCentralManagerDelegate?
    open var isScanning: Bool { false }
    public init(delegate: CBCentralManagerDelegate?, queue: DispatchQueue?, options: [String: Any]? = nil) {}
    open func retrievePeripherals(withIdentifiers identifiers: [UUID]) -> [CBPeripheral] { [] }
    open func retrieveConnectedPeripherals(withServices serviceUUIDs: [CBUUID]) -> [CBPeripheral] { [] }
    open func scanForPeripherals(withServices serviceUUIDs: [CBUUID]?, options: [String: Any]? = nil) {}
    open func stopScan() {}
    open func connect(_ peripheral: CBPeripheral, options: [String: Any]? = nil) {}
    open func cancelPeripheralConnection(_ peripheral: CBPeripheral) {}
    open func registerForConnectionEvents(options: [CBConnectionEventMatchingOption: Any]? = nil) {}
}
