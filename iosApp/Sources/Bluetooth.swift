import CoreBluetooth
import ExternalAccessory
import Foundation
import UIKit
import BlazbudsKit

/// The iPhone's Bluetooth for the shared app. iOS lists no paired devices and has no serial ports, so the earbuds
/// are the ones connected now that answer on a control service the app knows, found with
/// `retrieveConnectedPeripherals(withServices:)`: their control service runs over the same link as the music. Sony
/// and Bose earbuds are reached as accessories instead (ExternalAccessory).
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
        var lines: [String] = []
        var seen = Set<String>()
        let found: [(CBPeripheral, String)] = onQueue { connectedPeripherals() }
        for (peripheral, route) in found where seen.insert(peripheral.identifier.uuidString).inserted {
            lines.append(Bluetooth.line(peripheral.identifier.uuidString, peripheral.name ?? "Earbuds", route))
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
            central.scanForPeripherals(withServices: nil, options: [CBCentralManagerScanOptionAllowDuplicatesKey: false])
        } else {
            scanner = nil
            if central.isScanning { central.stopScan() }
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
        if let link = links[peripheral.identifier] {
            link.connected()
        } else if let check = probes[peripheral.identifier] {
            check.connected(peripheral)
        }
    }

    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        if let link = links[peripheral.identifier] {
            link.fail("Couldn't connect: \(error?.localizedDescription ?? "no reason given")")
        } else if let check = probes[peripheral.identifier] {
            check.failedToConnect(peripheral, error: error)
        }
    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        links[peripheral.identifier]?.disconnected(error)
        poke()
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String: Any], rssi RSSI: NSNumber) {
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
