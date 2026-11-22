// A CoreBluetooth stand-in whose CBUUID keeps its text, so Routes' logic can run.
@_exported import Foundation
open class CBUUID: NSObject {
    let text: String
    public init(string: String) { text = string.uppercased() }
    open var uuidString: String {
        let base = "-0000-1000-8000-00805F9B34FB"
        if text.hasSuffix(base) { let head = String(text.prefix(8)); return head.hasPrefix("0000") ? String(head.suffix(4)) : head }
        return text
    }
}
open class CBService: NSObject { open var uuid = CBUUID(string: "0"); open var characteristics: [CBCharacteristic]? { nil } }
public struct CBCharacteristicProperties: OptionSet { public let rawValue: UInt; public init(rawValue: UInt) { self.rawValue = rawValue }
  public static let read = Self(rawValue: 2), writeWithoutResponse = Self(rawValue: 4), write = Self(rawValue: 8), notify = Self(rawValue: 16), indicate = Self(rawValue: 32) }
open class CBCharacteristic: NSObject { open var uuid = CBUUID(string: "0"); open var properties: CBCharacteristicProperties = [] }
public let CBAdvertisementDataServiceUUIDsKey = "a", CBAdvertisementDataOverflowServiceUUIDsKey = "b", CBAdvertisementDataSolicitedServiceUUIDsKey = "c", CBAdvertisementDataServiceDataKey = "d"
