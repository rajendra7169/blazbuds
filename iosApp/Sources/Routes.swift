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

    /// The forms a UUID can take, full and upper case: as given, and with its bytes the other way round, as some
    /// earbuds send it (the Liberty 4 NC advertises its 014ef5da service as DAF54E01).
    static func forms(_ uuid: CBUUID) -> [String] {
        let id = full(uuid)
        if id.hasSuffix(base) {
            let head = String(id.prefix(8))
            let short = head.hasPrefix("0000") ? String(head.suffix(4)) : head
            let swapped = swapBytes(short)
            return [id, (swapped.count == 4 ? "0000" + swapped : swapped) + base]
        }
        let hex = id.replacingOccurrences(of: "-", with: "")
        guard hex.count == 32 else { return [id] }
        let swapped = Array(swapBytes(hex))
        let dashed = [0 ..< 8, 8 ..< 12, 12 ..< 16, 16 ..< 20, 20 ..< 32].map { String(swapped[$0]) }.joined(separator: "-")
        return [id, dashed]
    }

    /// Hex text with its bytes in the other order.
    static func swapBytes(_ hex: String) -> String {
        let digits = Array(hex)
        return stride(from: 0, to: digits.count - 1, by: 2).reversed().map { String(digits[$0 ..< $0 + 2]) }.joined()
    }

    /// Which route a service belongs to, if any, read either way round.
    static func route(of service: CBUUID) -> String? {
        for id in forms(service) {
            if id.hasSuffix("F5DA" + base) { return soundcore.name }
            for route in [nothing, melody, jieli, honor, sony, fastPair] where route.services.contains(where: { full($0) == id }) {
                return route.name
            }
        }
        return nil
    }

    /// Whether a characteristic is the 16-bit one given ("7777"), read either way round.
    static func isPart(_ characteristic: CBCharacteristic, _ short: String) -> Bool {
        forms(characteristic.uuid).contains("0000" + short + base)
    }

    /// A service holding Soundcore's write 7777 and notify 8888, whatever the service itself is called.
    static func soundcoreParts(_ service: CBService) -> Bool {
        let all = service.characteristics ?? []
        return all.contains { isPart($0, "7777") } && all.contains { isPart($0, "8888") }
    }

    // MARK: Earbuds seen over Bluetooth LE

    /// Name starts that are surely earbuds or headphones, so a scan can stop waiting once one shows.
    private static let earbudNames = [
        "soundcore", "liberty", "nothing", "ear (", "cmf ", "oneplus", "nord buds", "oppo enco", "realme", "ultima",
        "earbuds x", "honor", "huawei", "freebuds", "wf-", "wh-", "linkbuds",
    ]

    static func looksLikeEarbuds(_ name: String) -> Bool {
        let lower = name.lowercased()
        return earbudNames.contains { lower.hasPrefix($0) || lower.contains(" " + $0) }
    }

    /// The name without the " LE" some earbuds add to their Bluetooth LE side ("soundcore Liberty 4 NC LE").
    static func cleanName(_ name: String) -> String {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        for tail in [" LE", "-LE", "_LE", " (LE)"] where trimmed.hasSuffix(tail) {
            return String(trimmed.dropLast(tail.count)).trimmingCharacters(in: .whitespaces)
        }
        return trimmed
    }

    /// Every service UUID an advertisement mentions.
    static func advertised(_ advertisement: [String: Any]) -> [CBUUID] {
        var all = advertisement[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID] ?? []
        all += advertisement[CBAdvertisementDataOverflowServiceUUIDsKey] as? [CBUUID] ?? []
        all += advertisement[CBAdvertisementDataSolicitedServiceUUIDsKey] as? [CBUUID] ?? []
        all += (advertisement[CBAdvertisementDataServiceDataKey] as? [CBUUID: Data] ?? [:]).keys
        return all
    }

    /// How a peripheral seen in a scan would be reached: the route of a control service it advertises (either byte
    /// order), "scan" for a name that is surely earbuds, "fastpair" for Fast Pair alone; nil for anything else.
    static func match(name: String?, services: [CBUUID]) -> String? {
        let routes = services.compactMap { route(of: $0) }
        if let control = routes.first(where: { $0 != fastPair.name }) { return control }
        if let name = name, looksLikeEarbuds(name) { return "scan" }
        return routes.isEmpty ? nil : fastPair.name
    }

    /// Whether a scan match is surely earbuds, so waiting for one can stop.
    static func isSure(_ route: String) -> Bool { route != fastPair.name }

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
            let write = all.first { forms($0.uuid).contains { $0.hasPrefix(pair.write) } }
            let notify = all.first { forms($0.uuid).contains { $0.hasPrefix(pair.notify) } }
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
