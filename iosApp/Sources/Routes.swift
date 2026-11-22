import CoreBluetooth
import Foundation

/// One way into a brand's earbuds over Bluetooth LE: the services that answer for it.
struct Route {
    let name: String
    let services: [CBUUID]
}

/// The control services the app knows, in the order they are preferred.
enum Routes {
    /// The Bluetooth base the short UUIDs sit in.
    static let base = "-0000-1000-8000-00805F9B34FB"

    /// Soundcore models each have their own `XXXXf5da` service (Liberty 4 NC: `014ef5da`), so a range is asked for.
    static let soundcoreKnown = ["014E", "0209", "020C", "011C"]
    static let soundcore = Route(
        name: "soundcore",
        services: (0x0000...0x03FF).map { CBUUID(string: String(format: "%04X", $0) + "F5DA" + base) }
    )
    static let nothing = Route(name: "nothing", services: [CBUUID(string: "FD90")])
    static let melody = Route(name: "melody", services: [CBUUID(string: "079A"), CBUUID(string: "0000079A-D102-11E1-9B23-00025B00A5A5")])
    static let jieli = Route(name: "jieli", services: [CBUUID(string: "AE00")])
    static let honor = Route(name: "honor", services: [CBUUID(string: "FD9A")])
    static let sony = Route(name: "sony", services: [CBUUID(string: "5B833E20-6BC7-4802-8E9A-723CECA4BD8F")])
    /// Google Fast Pair, which many earbuds offer: only a way to find them, not to control them.
    static let fastPair = Route(name: "fastpair", services: [CBUUID(string: "FE2C")])

    static let all = [soundcore, nothing, melody, jieli, honor, sony, fastPair]

    /// The services connection events are asked for: the known ones only, as that list is kept short.
    static var watched: [CBUUID] {
        soundcoreKnown.map { CBUUID(string: $0 + "F5DA" + base) }
            + nothing.services + melody.services + jieli.services + honor.services + sony.services + fastPair.services
    }

    /// The full 128-bit form of a UUID, upper case, whether iOS gives it short or long.
    static func full(_ uuid: CBUUID) -> String {
        let text = uuid.uuidString.uppercased()
        switch text.count {
        case 4: return "0000" + text + base
        case 8: return text + base
        default: return text
        }
    }

    /// Which route a service belongs to, if any.
    static func route(of service: CBUUID) -> String? {
        let id = full(service)
        if id.hasSuffix("F5DA" + base) { return soundcore.name }
        for route in [nothing, melody, jieli, honor, sony, fastPair] where route.services.contains(where: { full($0) == id }) {
            return route.name
        }
        return nil
    }

    /// The control service to use among those a peripheral offers, best first; Fast Pair never controls anything.
    static func pick(_ services: [CBService]) -> (service: CBService, route: String)? {
        let order = [soundcore.name, nothing.name, melody.name, jieli.name, honor.name, sony.name]
        let found = services.compactMap { service in route(of: service.uuid).map { (service: service, route: $0) } }
        for name in order {
            if let hit = found.first(where: { $0.route == name }) { return hit }
        }
        return nil
    }

    /// Characteristic pairs (write, notify) whose roles are known; anything else is chosen by what it can do.
    private static let knownPairs: [(write: String, notify: String)] = [
        ("00007777" + base, "00008888" + base),
        ("0100079A", "0200079A"),
        ("0000AE01" + base, "0000AE02" + base),
        ("5B833C60", "5B833C61"),
    ]

    /// The characteristic to write the earbuds' messages to and the one their answers come on.
    static func pickCharacteristics(_ all: [CBCharacteristic]) -> (write: CBCharacteristic, notify: CBCharacteristic)? {
        for pair in knownPairs {
            let write = all.first { full($0.uuid).hasPrefix(pair.write) }
            let notify = all.first { full($0.uuid).hasPrefix(pair.notify) }
            if let write = write, let notify = notify { return (write, notify) }
        }
        let canWrite = all.filter { $0.properties.contains(.write) || $0.properties.contains(.writeWithoutResponse) }
        let canNotify = all.filter { $0.properties.contains(.notify) || $0.properties.contains(.indicate) }
        guard let notify = canNotify.first, let write = canWrite.first(where: { $0 !== notify }) ?? canWrite.first else { return nil }
        return (write, notify)
    }

    /// What a characteristic can do, in short words, for the Bluetooth check.
    static func describe(_ properties: CBCharacteristicProperties) -> String {
        var words: [String] = []
        if properties.contains(.read) { words.append("read") }
        if properties.contains(.write) { words.append("write") }
        if properties.contains(.writeWithoutResponse) { words.append("write-no-reply") }
        if properties.contains(.notify) { words.append("notify") }
        if properties.contains(.indicate) { words.append("indicate") }
        return words.isEmpty ? "-" : words.joined(separator: ", ")
    }

    /// Bytes as spaced hex, cut after [limit] bytes.
    static func hex(_ data: Data, limit: Int = 64) -> String {
        let shown = data.prefix(limit).map { String(format: "%02x", $0) }.joined(separator: " ")
        return data.count > limit ? shown + " … (\(data.count) bytes)" : shown
    }
}

extension Array {
    /// The array in pieces of at most [size].
    func chunked(_ size: Int) -> [[Element]] {
        stride(from: 0, to: count, by: size).map { Array(self[$0 ..< Swift.min($0 + size, count)]) }
    }
}
