import CoreBluetooth
import ExternalAccessory
import Foundation
import UIKit
import BlazbudsKit

/// The iPhone's Bluetooth for the shared app. iOS lists no paired devices and has no serial ports, so the earbuds
/// are found three ways:
/// - connected now on a control service the app knows (`retrieveConnectedPeripherals(withServices:)`);
/// - remembered: earbuds that have answered the app before, by the id iOS gave them. A connect to them waits inside
///   iOS until they are in reach, then completes by itself;
/// - seen over Bluetooth LE: when nothing else is found the app scans, and takes what advertises a control service
///   (either byte order) or has an earbuds name. The Liberty 4 NC only advertises in pairing mode, so the first
///   connection needs it.
/// Sony and Bose earbuds are reached as accessories instead (ExternalAccessory).
///
/// Every CoreBluetooth call and event happens on `queue`.
final class Bluetooth: NSObject, BluetoothBridge, CBCentralManagerDelegate {
    static let shared = Bluetooth()

    let queue = DispatchQueue(label: "com.blazify.buds.bluetooth")
    private let queueKey = DispatchSpecificKey<Bool>()
    private(set) var central: CBCentralManager!

    /// Open links, by peripheral.
    private var links: [UUID: BleLink] = [:]
    /// Peripherals the Bluetooth check is looking at.
    private var probes: [UUID: BluetoothCheck] = [:]
    /// The check that is scanning, if any.
    private var scanner: BluetoothCheck?
    private var changed: (() -> Void)?

    /// Earbuds that have answered the app, by peripheral id: name and route, split by a tab. Kept across launches.
    private var remembered: [String: String] = UserDefaults.standard.dictionary(forKey: Bluetooth.rememberedKey) as? [String: String] ?? [:]
    private static let rememberedKey = "blazbuds.remembered_earbuds"
    /// Remembered earbuds with a connect waiting inside iOS.
    private var pending = Set<UUID>()

    /// Earbuds seen advertising lately.
    private var nearby: [UUID: Sighting] = [:]
    private struct Sighting {
        let peripheral: CBPeripheral
        let name: String
        let route: String
        let at: Date
    }
    /// Until when the finder's own scan runs, while nothing else is found.
    private var findUntil: Date?
    /// A look that waits for the scan to see earbuds.
    private var waitingForSighting: DispatchSemaphore?

    override init() {
        super.init()
        queue.setSpecific(key: queueKey, value: true)
        var options: [String: Any] = [CBCentralManagerOptionShowPowerAlertKey: false]
        // Restoring Bluetooth after iOS closed the app in the background needs the bluetooth-central mode, which the
        // app declares; asking for it without the mode would stop the app.
        let modes = Bundle.main.object(forInfoDictionaryKey: "UIBackgroundModes") as? [String] ?? []
        if modes.contains("bluetooth-central") {
            options[CBCentralManagerOptionRestoreIdentifierKey] = "com.blazify.buds.central"
        }
        central = CBCentralManager(delegate: self, queue: queue, options: options)
        EAAccessoryManager.shared().registerForLocalNotifications()
        for name in [Notification.Name.EAAccessoryDidConnect, .EAAccessoryDidDisconnect] {
            _ = NotificationCenter.default.addObserver(forName: name, object: nil, queue: .main) { [weak self] _ in self?.poke() }
        }
    }

    /// Runs [work] on the Bluetooth queue and returns what it gives.
    func onQueue<T>(_ work: () -> T) -> T {
        if DispatchQueue.getSpecific(key: queueKey) == true { return work() }
        return queue.sync(execute: work)
    }

    /// Waits a moment for iOS to say whether Bluetooth is on, as it does just after the app starts.
    private func waitForState() {
        if DispatchQueue.getSpecific(key: queueKey) == true || Thread.isMainThread { return }
        for _ in 0 ..< 30 {
            let state = onQueue { central.state }
            if state != .unknown && state != .resetting { return }
            Thread.sleep(forTimeInterval: 0.1)
        }
    }

    // MARK: BluetoothBridge

    func isOn() -> Bool {
        switch onQueue({ central.state }) {
        case .poweredOff, .unauthorized, .unsupported: return false
        default: return true
        }
    }

    func connectedEarbuds() -> [String] {
        waitForState()
        var found: [(id: UUID, name: String, route: String)] = onQueue { earbudsNow() }
        // Fast Pair alone is offered by speakers and screens too, so it doesn't count as earbuds found.
        if !found.contains(where: { Routes.isSure($0.route) }) && !Thread.isMainThread {
            // Nothing in reach the usual ways: look over Bluetooth LE for a few seconds, and stop as soon as earbuds show.
            if let wait = onQueue({ startFinding() }) {
                _ = wait.wait(timeout: .now() + 4)
                found = onQueue { earbudsNow() }
            }
        }
        var lines: [String] = []
        var seen = Set<UUID>()
        for entry in found where seen.insert(entry.id).inserted {
            lines.append(Bluetooth.line(entry.id.uuidString, entry.name, entry.route))
        }
        for accessory in Accessories.connected() {
            lines.append(Bluetooth.line("ea:\(accessory.connectionID)", accessory.name, "ea"))
        }
        return lines
    }

    func connect(id: String, route: String, pipe: BudsPipe) {
        if id.hasPrefix("ea:") {
            AccessoryLink.open(id: id, route: route, pipe: pipe)
            return
        }
        queue.async { [self] in
            guard central.state == .poweredOn else {
                pipe.failed(reason: "Bluetooth is off or not allowed for Blazbuds", busy: false)
                return
            }
            guard let uuid = UUID(uuidString: id), let peripheral = central.retrievePeripherals(withIdentifiers: [uuid]).first else {
                pipe.failed(reason: "The iPhone doesn't know these earbuds now", busy: false)
                return
            }
            // A link left from before steps aside; the connection itself stays, so the new one starts at once.
            links[uuid]?.retire()
            pending.remove(uuid)
            let link = BleLink(peripheral: peripheral, pipe: pipe, owner: self)
            links[uuid] = link
            probes[uuid] = nil
            link.start()
        }
    }

    func check(done: @escaping (String) -> Void) {
        // What only the main thread may read is read first; the Bluetooth queue never waits on the main thread,
        // as the main thread waits on it (isOn).
        DispatchQueue.main.async { [self] in
            let device = "iOS \(UIDevice.current.systemVersion), \(UIDevice.current.model)"
            let accessories = BluetoothCheck.accessories()
            queue.async { [self] in
                BluetoothCheck(owner: self, device: device, accessories: accessories, done: done).run()
            }
        }
    }

    func watch(changed: @escaping () -> Void) {
        queue.async { [self] in
            self.changed = changed
            watchConnections()
        }
    }

    // MARK: For the links and the check

    /// The peripherals connected now on any known service, each with the route it was found by.
    func connectedPeripherals() -> [(CBPeripheral, String)] {
        guard central.state == .poweredOn else { return [] }
        var found: [(CBPeripheral, String)] = []
        var seen = Set<UUID>()
        for route in Routes.all {
            for part in route.services.chunked(64) {
                for peripheral in central.retrieveConnectedPeripherals(withServices: part) where seen.insert(peripheral.identifier).inserted {
                    found.append((peripheral, route.name))
                }
            }
        }
        // Said once per change, so a report shows what the phone was asked and what it answered.
        let said = found.isEmpty ? "none" : found.map { "\($0.0.name ?? "no name") (\($0.1))" }.joined(separator: ", ")
        if said != lastFound {
            lastFound = said
            BudsLog.shared.add(line: "iPhone connected earbuds on a known control service: \(said)")
        }
        return found
    }

    /// What the last look found, so the log notes only changes.
    private var lastFound = ""

    /// Every pair in reach now, each way: connected on a control service, open in a link, remembered and connected,
    /// or seen advertising in the last minute.
    private func earbudsNow() -> [(id: UUID, name: String, route: String)] {
        var found = connectedPeripherals().map { (id: $0.0.identifier, name: $0.0.name ?? "Earbuds", route: $0.1) }
        for (id, link) in links where link.peripheral.state == .connected {
            found.append((id, Routes.cleanName(link.peripheral.name ?? "Earbuds"), link.route.isEmpty ? (rememberedRoute(id) ?? "scan") : link.route))
        }
        let ids = remembered.keys.compactMap { UUID(uuidString: $0) }
        if central.state == .poweredOn && !ids.isEmpty {
            for peripheral in central.retrievePeripherals(withIdentifiers: ids) {
                let id = peripheral.identifier
                let parts = (remembered[id.uuidString] ?? "").components(separatedBy: "\t")
                let name = parts.first.flatMap { $0.isEmpty ? nil : $0 } ?? Routes.cleanName(peripheral.name ?? "Earbuds")
                if peripheral.state == .connected {
                    found.append((id, name, parts.count > 1 ? parts[1] : "scan"))
                } else if links[id] == nil && !pending.contains(id) {
                    // Waits inside iOS until the earbuds are in reach; didConnect then has the app look again.
                    pending.insert(id)
                    BudsLog.shared.add(line: "iPhone: waiting for remembered \(name) [\(id.uuidString.prefix(8))] to come in reach")
                    central.connect(peripheral, options: nil)
                }
            }
        }
        let fresh = Date().addingTimeInterval(-60)
        for (id, seen) in nearby where seen.at > fresh {
            found.append((id, seen.name, seen.route))
        }
        return found
    }

    private func rememberedRoute(_ id: UUID) -> String? {
        let parts = (remembered[id.uuidString] ?? "").components(separatedBy: "\t")
        return parts.count > 1 ? parts[1] : nil
    }

    /// The earbuds answered on this peripheral: it is found by its id from now on, even before it connects.
    func remember(_ peripheral: CBPeripheral, route: String) {
        let id = peripheral.identifier.uuidString
        let name = Routes.cleanName(peripheral.name ?? "Earbuds")
        let value = "\(name)\t\(route)"
        guard remembered[id] != value else { return }
        remembered[id] = value
        UserDefaults.standard.set(remembered, forKey: Bluetooth.rememberedKey)
        BudsLog.shared.add(line: "iPhone: remembered \(name) as [\(id.prefix(8))] (\(route))")
    }

    /// The remembered earbuds, as lines for the Bluetooth check.
    func rememberedLines() -> [String] {
        guard !remembered.isEmpty else { return ["Remembered: none yet"] }
        let ids = remembered.keys.compactMap { UUID(uuidString: $0) }
        let known = Dictionary(uniqueKeysWithValues: central.retrievePeripherals(withIdentifiers: ids).map { ($0.identifier.uuidString, $0) })
        return ["Remembered:"] + remembered.sorted { $0.key < $1.key }.map { id, value in
            let parts = value.components(separatedBy: "\t")
            let state = known[id].map { $0.state == .connected ? "connected to the app" : "not in reach" } ?? "unknown to iOS now"
            return "  \(parts.first ?? "?") [\(id.prefix(8))] (\(parts.count > 1 ? parts[1] : "?")), \(state)\(pending.contains(UUID(uuidString: id) ?? UUID()) ? ", waiting to connect" : "")"
        }
    }

    /// Starts or extends the finder's scan; returns something to wait on unless earbuds were seen already.
    private func startFinding() -> DispatchSemaphore? {
        guard central.state == .poweredOn else { return nil }
        findUntil = Date().addingTimeInterval(15)
        if !central.isScanning {
            BudsLog.shared.add(line: "iPhone: no earbuds in reach; looking over Bluetooth LE")
            central.scanForPeripherals(withServices: nil, options: [CBCentralManagerScanOptionAllowDuplicatesKey: false])
        }
        queue.asyncAfter(deadline: .now() + 15.5) { [self] in stopScanIfDone() }
        let fresh = Date().addingTimeInterval(-60)
        if nearby.values.contains(where: { $0.at > fresh && Routes.isSure($0.route) }) { return nil }
        let wait = DispatchSemaphore(value: 0)
        waitingForSighting?.signal()
        waitingForSighting = wait
        return wait
    }

    private func stopScanIfDone() {
        let finding = findUntil.map { $0 > Date() } ?? false
        if !finding { findUntil = nil }
        if !finding && scanner == nil && central.isScanning {
            central.stopScan()
            BudsLog.shared.add(line: "iPhone: stopped looking over Bluetooth LE (\(nearby.count) earbuds seen)")
        }
    }

    /// A peripheral the scan saw: kept when it looks like earbuds.
    private func sighted(_ peripheral: CBPeripheral, advertisement: [String: Any], rssi: NSNumber) {
        let raw = (advertisement[CBAdvertisementDataLocalNameKey] as? String) ?? peripheral.name
        let services = Routes.advertised(advertisement)
        guard let route = Routes.match(name: raw, services: services) else { return }
        let id = peripheral.identifier
        let name = Routes.cleanName(raw ?? "Earbuds")
        let first = nearby[id] == nil
        nearby[id] = Sighting(peripheral: peripheral, name: name, route: route, at: Date())
        guard first else { return }
        let listed = services.map { $0.uuidString }.joined(separator: ", ")
        BudsLog.shared.add(line: "iPhone: saw \(raw ?? "no name") [\(id.uuidString.prefix(8))] over Bluetooth LE, \(rssi) dBm, route \(route)\(listed.isEmpty ? "" : ", services " + listed)")
        if Routes.isSure(route) {
            waitingForSighting?.signal()
            waitingForSighting = nil
            poke()
        }
    }

    func link(for id: UUID) -> BleLink? { links[id] }

    func forget(_ link: BleLink) {
        if links[link.peripheral.identifier] === link { links[link.peripheral.identifier] = nil }
    }

    func probe(_ peripheral: CBPeripheral, by check: BluetoothCheck?) {
        probes[peripheral.identifier] = check
    }

    func scan(by check: BluetoothCheck?) {
        if let check = check {
            scanner = check
            if !central.isScanning {
                central.scanForPeripherals(withServices: nil, options: [CBCentralManagerScanOptionAllowDuplicatesKey: false])
            }
        } else {
            scanner = nil
            stopScanIfDone()
        }
    }

    /// Something changed that the earbuds list may show: the app looks again.
    func poke() {
        guard let changed = changed else { return }
        DispatchQueue.main.async { changed() }
    }

    private func watchConnections() {
        guard central.state == .poweredOn else { return }
        // Tells the app when earbuds offering a known service connect or disconnect, even over the classic link.
        central.registerForConnectionEvents(options: [.serviceUUIDs: Routes.watched])
    }

    private static func line(_ id: String, _ name: String, _ route: String) -> String {
        let clean = name.replacingOccurrences(of: "\t", with: " ").replacingOccurrences(of: "\n", with: " ")
        return "\(id)\t\(clean)\t\(route)"
    }

    // MARK: CBCentralManagerDelegate

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        BudsLog.shared.add(line: "iPhone Bluetooth: \(Bluetooth.describe(central.state))")
        if central.state == .poweredOn { watchConnections() }
        poke()
    }

    func centralManager(_ central: CBCentralManager, willRestoreState dict: [String: Any]) {
        let restored = (dict[CBCentralManagerRestoredStatePeripheralsKey] as? [CBPeripheral])?.count ?? 0
        BudsLog.shared.add(line: "iPhone Bluetooth restored after a relaunch (\(restored) peripherals)")
    }

    func centralManager(_ central: CBCentralManager, connectionEventDidOccur event: CBConnectionEvent, for peripheral: CBPeripheral) {
        BudsLog.shared.add(line: "iPhone: \(peripheral.name ?? "a device") \(event == .peerConnected ? "connected" : "disconnected")")
        poke()
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        let id = peripheral.identifier
        if let link = links[id] {
            link.connected()
        } else if let check = probes[id] {
            check.connected(peripheral)
        } else if pending.remove(id) != nil {
            BudsLog.shared.add(line: "iPhone: remembered \(peripheral.name ?? "earbuds") [\(id.uuidString.prefix(8))] came in reach and connected")
            poke()
        }
    }

    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        let id = peripheral.identifier
        if let link = links[id] {
            link.fail("Couldn't connect: \(error?.localizedDescription ?? "no reason given")")
        } else if let check = probes[id] {
            check.failedToConnect(peripheral, error: error)
        } else if pending.remove(id) != nil {
            BudsLog.shared.add(line: "iPhone: couldn't connect remembered \(peripheral.name ?? "earbuds") [\(id.uuidString.prefix(8))]: \(error?.localizedDescription ?? "no reason given")")
        }
    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        let id = peripheral.identifier
        if links[id] == nil && remembered[id.uuidString] != nil {
            BudsLog.shared.add(line: "iPhone: \(peripheral.name ?? "earbuds") [\(id.uuidString.prefix(8))] disconnected\(error.map { ": \($0.localizedDescription)" } ?? "")")
        }
        links[id]?.disconnected(error)
        poke()
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String: Any], rssi RSSI: NSNumber) {
        sighted(peripheral, advertisement: advertisementData, rssi: RSSI)
        scanner?.discovered(peripheral, advertisement: advertisementData, rssi: RSSI)
    }

    static func describe(_ state: CBManagerState) -> String {
        switch state {
        case .poweredOn: return "on"
        case .poweredOff: return "off"
        case .unauthorized: return "not allowed"
        case .unsupported: return "unsupported"
        case .resetting: return "resetting"
        case .unknown: return "not known yet"
        @unknown default: return "state \(state.rawValue)"
        }
    }

    static func describe(_ authorization: CBManagerAuthorization) -> String {
        switch authorization {
        case .allowedAlways: return "allowed"
        case .denied: return "denied"
        case .restricted: return "restricted"
        case .notDetermined: return "not asked yet"
        @unknown default: return "authorization \(authorization.rawValue)"
        }
    }
}

/// Sony and Bose earbuds reached as accessories, through the protocols the app declares in its Info.plist.
enum Accessories {
    static let sony = ["jp.co.sony.songpal.mdr.link2", "jp.co.sony.songpal.mdr.link"]
    static let bose = ["com.bose.bmap2", "com.bose.bmap"]
    static let known = sony + bose

    /// Accessories connected now that speak a known protocol. Only Sony's have a driver; Bose show the basics.
    static func connected() -> [EAAccessory] {
        let read = { EAAccessoryManager.shared().connectedAccessories.filter { !Set($0.protocolStrings).isDisjoint(with: known) } }
        return Thread.isMainThread ? read() : DispatchQueue.main.sync(execute: read)
    }
}
