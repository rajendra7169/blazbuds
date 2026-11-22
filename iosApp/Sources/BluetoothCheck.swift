import CoreBluetooth
import ExternalAccessory
import Foundation
import BlazbudsKit

/// Help › Bluetooth check: everything iOS reports about Bluetooth and the earbuds, as text to paste into a report.
/// It lists the peripherals connected on each known service, opens each one the app isn't using to list its
/// services and parts and note its first replies (asking a Soundcore pair for its state, which changes nothing),
/// lists the accessories and the remembered earbuds, and scans a few seconds for what is nearby. Up to two earbuds
/// the scan finds (by name, or by a control service either byte order) are then connected and looked at the same
/// way. It runs on the owner's queue and ends within about half a minute whatever happens.
final class BluetoothCheck: NSObject, CBPeripheralDelegate {
    private let owner: Bluetooth
    private let device: String
    private let accessoryLines: [String]
    private let done: (String) -> Void

    private var lines: [String] = []
    private var finished = false

    /// Peripherals still to look at, and the one being looked at.
    private var queueToProbe: [CBPeripheral] = []
    private var current: CBPeripheral?
    private var currentLines: [String] = []
    private var servicesLeft = 0
    private var listening: [CBCharacteristic] = []
    private var replies = 0
    private var probeNumber = 0

    /// What the scan saw, by peripheral.
    private var nearby: [UUID: String] = [:]
    private var scanning = false
    /// Earbuds the scan found, to connect and look at once it ends.
    private var found: [CBPeripheral] = []
    private var looked = Set<UUID>()

    /// The Soundcore "send me everything" request, which only reads.
    private static let soundcoreState = Data([0x08, 0xEE, 0x00, 0x00, 0x00, 0x01, 0x01, 0x0A, 0x00, 0x02])

    init(owner: Bluetooth, device: String, accessories: [String], done: @escaping (String) -> Void) {
        self.owner = owner
        self.device = device
        self.accessoryLines = accessories
        self.done = done
    }

    func run() {
        let central = owner.central!
        lines.append("Blazbuds Bluetooth check · \(device)")
        lines.append("Bluetooth: \(Bluetooth.describe(central.state)) · permission: \(Bluetooth.describe(CBManager.authorization))")
        let declared = Bundle.main.object(forInfoDictionaryKey: "UISupportedExternalAccessoryProtocols") as? [String] ?? []
        lines.append("App: \(Bundle.main.bundleIdentifier ?? "?") · accessory protocols it can open: \(declared.isEmpty ? "none (inside LiveContainer?)" : declared.joined(separator: ", "))")
        lines.append(contentsOf: accessoryLines)
        if central.state == .poweredOn { lines.append(contentsOf: owner.rememberedLines()) }
        guard central.state == .poweredOn else {
            lines.append("Bluetooth isn't on and allowed, so nothing more can be seen.")
            finish()
            return
        }
        lines.append("")
        lines.append("Connected, by the service that answered:")
        var seen = Set<UUID>()
        for route in Routes.all {
            var found: [CBPeripheral] = []
            for part in route.services.chunked(64) {
                for peripheral in central.retrieveConnectedPeripherals(withServices: part) where !found.contains(where: { $0.identifier == peripheral.identifier }) {
                    found.append(peripheral)
                }
            }
            let names = found.map { "\($0.name ?? "no name") [\(short($0.identifier))]" }
            lines.append("  \(route.name): \(names.isEmpty ? "none" : names.joined(separator: ", "))")
            for peripheral in found where seen.insert(peripheral.identifier).inserted {
                queueToProbe.append(peripheral)
            }
        }
        queueToProbe = Array(queueToProbe.prefix(4))
        // A short look around, for earbuds whose control service sits on a Bluetooth LE side of their own.
        scanning = true
        owner.scan(by: self)
        owner.queue.asyncAfter(deadline: .now() + 6) { [self] in endScan() }
        // Whatever happens, the report comes back.
        owner.queue.asyncAfter(deadline: .now() + 30) { [self] in
            if !finished { lines.append("(the check ran out of time here)") }
            endScan()
            endProbe()
            finish()
        }
        probeNext()
    }

    private func short(_ id: UUID) -> String { String(id.uuidString.prefix(8)) }

    /// The accessories connected now, read on the main thread before the check starts.
    static func accessories() -> [String] {
        let all = EAAccessoryManager.shared().connectedAccessories
        var lines: [String] = []
        if all.isEmpty {
            lines.append("Accessories: none")
        } else {
            lines.append("Accessories:")
            for accessory in all {
                lines.append("  \(accessory.name) · \(accessory.manufacturer) · model \(accessory.modelNumber) · firmware \(accessory.firmwareRevision) · id \(accessory.connectionID) · protocols: \(accessory.protocolStrings.isEmpty ? "none" : accessory.protocolStrings.joined(separator: ", "))")
            }
        }
        return lines
    }

    // MARK: Looking at each connected peripheral

    private func probeNext() {
        guard !finished else { return }
        guard !queueToProbe.isEmpty else {
            if !scanning { finish() }
            return
        }
        let peripheral = queueToProbe.removeFirst()
        looked.insert(peripheral.identifier)
        lines.append("")
        lines.append("\(peripheral.name ?? "no name") [\(short(peripheral.identifier))], \(describe(peripheral.state)):")
        if let link = owner.link(for: peripheral.identifier) {
            lines.append("  in use by the app now:")
            lines.append(contentsOf: link.summary.map { "  " + $0 })
            probeNext()
            return
        }
        current = peripheral
        currentLines = []
        servicesLeft = 0
        listening = []
        replies = 0
        probeNumber += 1
        let number = probeNumber
        owner.probe(peripheral, by: self)
        peripheral.delegate = self
        let wasConnected = peripheral.state == .connected
        if wasConnected {
            peripheral.discoverServices(nil)
        } else {
            currentLines.append("  connecting…")
            BudsLog.shared.add(line: "Bluetooth check: connecting \(peripheral.name ?? "no name") [\(short(peripheral.identifier))]")
            owner.central.connect(peripheral, options: nil)
        }
        // Each one gets a few seconds, a few more when it has to connect first.
        let allowed: Double = wasConnected ? 5 : 8
        owner.queue.asyncAfter(deadline: .now() + allowed) { [self] in
            if probeNumber == number && current != nil {
                currentLines.append("  (no more within \(Int(allowed)) s)")
                endProbe()
                probeNext()
            }
        }
    }

    func connected(_ peripheral: CBPeripheral) {
        guard peripheral === current else { return }
        currentLines.append("  connected")
        peripheral.discoverServices(nil)
    }

    func failedToConnect(_ peripheral: CBPeripheral, error: Error?) {
        guard peripheral === current else { return }
        currentLines.append("  couldn't connect: \(error?.localizedDescription ?? "no reason given")")
        endProbe()
        probeNext()
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard peripheral === current else { return }
        if let error = error { currentLines.append("  services: error \(error.localizedDescription)") }
        let services = peripheral.services ?? []
        if services.isEmpty {
            currentLines.append("  no services")
            endProbe()
            probeNext()
            return
        }
        servicesLeft = services.count
        for service in services { peripheral.discoverCharacteristics(nil, for: service) }
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        guard peripheral === current else { return }
        let route = Routes.route(of: service.uuid).map { " (\($0))" } ?? ""
        currentLines.append("  service \(Routes.full(service.uuid))\(route)\(error.map { ": error \($0.localizedDescription)" } ?? "")")
        for characteristic in service.characteristics ?? [] {
            currentLines.append("    \(Routes.full(characteristic.uuid)) [\(Routes.describe(characteristic.properties))]")
        }
        servicesLeft -= 1
        if servicesLeft == 0 { listen(peripheral) }
    }

    /// Listens on the control services' notify parts, then asks a Soundcore pair for its state.
    private func listen(_ peripheral: CBPeripheral) {
        // Known control services, read either byte order, and any service holding Soundcore's 7777 and 8888.
        let control = (peripheral.services ?? []).filter {
            (Routes.route(of: $0.uuid) != nil && Routes.route(of: $0.uuid) != Routes.fastPair.name) || Routes.soundcoreParts($0)
        }
        guard !control.isEmpty else {
            currentLines.append("  no control service the app knows")
            endProbe()
            probeNext()
            return
        }
        for service in control {
            for characteristic in service.characteristics ?? [] where characteristic.properties.contains(.notify) || characteristic.properties.contains(.indicate) {
                if listening.count < 4 {
                    listening.append(characteristic)
                    peripheral.setNotifyValue(true, for: characteristic)
                }
            }
        }
        currentLines.append("  max write: \(peripheral.maximumWriteValueLength(for: .withoutResponse)) without reply, \(peripheral.maximumWriteValueLength(for: .withResponse)) with")
        let number = probeNumber
        owner.queue.asyncAfter(deadline: .now() + 0.6) { [self] in
            guard probeNumber == number, current === peripheral else { return }
            let parts = control.flatMap { $0.characteristics ?? [] }
            if let write = parts.first(where: { Routes.isPart($0, "7777") }) {
                let type: CBCharacteristicWriteType = write.properties.contains(.writeWithoutResponse) ? .withoutResponse : .withResponse
                peripheral.writeValue(BluetoothCheck.soundcoreState, for: write, type: type)
                currentLines.append("  sent the state request to \(Routes.full(write.uuid).prefix(8)): \(Routes.hex(BluetoothCheck.soundcoreState))")
            }
        }
        owner.queue.asyncAfter(deadline: .now() + 3) { [self] in
            guard probeNumber == number, current === peripheral else { return }
            if replies == 0 { currentLines.append("  no replies within 3 s") }
            endProbe()
            probeNext()
        }
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateNotificationStateFor characteristic: CBCharacteristic, error: Error?) {
        guard peripheral === current, let error = error else { return }
        currentLines.append("  couldn't listen on \(Routes.full(characteristic.uuid)): \(error.localizedDescription)")
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        guard peripheral === current, let value = characteristic.value, replies < 6 else { return }
        replies += 1
        currentLines.append("  reply on \(Routes.full(characteristic.uuid).prefix(8)): \(Routes.hex(value, limit: 48))")
    }

    func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {
        guard peripheral === current, let error = error else { return }
        currentLines.append("  the write failed: \(error.localizedDescription)")
    }

    /// Stops listening and hands the peripheral back; the connection stays, as the earbuds play music on it.
    private func endProbe() {
        guard let peripheral = current else { return }
        for characteristic in listening where characteristic.isNotifying {
            peripheral.setNotifyValue(false, for: characteristic)
        }
        if peripheral.delegate === self { peripheral.delegate = nil }
        owner.probe(peripheral, by: nil)
        lines.append(contentsOf: currentLines)
        currentLines = []
        listening = []
        current = nil
    }

    // MARK: The scan

    func discovered(_ peripheral: CBPeripheral, advertisement: [String: Any], rssi: NSNumber) {
        let name = (advertisement[CBAdvertisementDataLocalNameKey] as? String) ?? peripheral.name
        let advertised = Routes.advertised(advertisement)
        // Earbuds by name or by a control service, either byte order: looked at more closely once the scan ends.
        if let route = Routes.match(name: name, services: advertised), Routes.isSure(route), found.count < 2,
           !found.contains(where: { $0.identifier == peripheral.identifier }) {
            found.append(peripheral)
        }
        guard nearby.count < 25 else { return }
        let services = advertised.map { $0.uuidString }
        // Unnamed devices with no services are mostly phones and tags around; they say nothing useful.
        guard name != nil || !services.isEmpty else { return }
        nearby[peripheral.identifier] = "\(name ?? "no name") [\(short(peripheral.identifier))] \(rssi) dBm\(services.isEmpty ? "" : " · " + services.joined(separator: ", "))"
    }

    private func endScan() {
        guard scanning else { return }
        scanning = false
        owner.scan(by: nil)
        let more = found.filter { !looked.contains($0.identifier) && !queueToProbe.contains($0) }
        if !more.isEmpty {
            lines.append("")
            lines.append("Found over Bluetooth LE, looked at next: \(more.map { "\($0.name ?? "no name") [\(short($0.identifier))]" }.joined(separator: ", "))")
            queueToProbe.append(contentsOf: more)
        }
        if current == nil { probeNext() }
    }

    private func finish() {
        guard !finished else { return }
        finished = true
        endProbe()
        lines.append("")
        lines.append(nearby.isEmpty ? "Nearby (6 s scan): nothing with a name" : "Nearby (6 s scan):")
        lines.append(contentsOf: nearby.values.sorted().map { "  " + $0 })
        let text = lines.joined(separator: "\n")
        BudsLog.shared.add(line: "Bluetooth check done")
        done(text)
    }

    private func describe(_ state: CBPeripheralState) -> String {
        switch state {
        case .connected: return "connected"
        case .connecting: return "connecting"
        case .disconnected: return "not connected to the app yet"
        case .disconnecting: return "disconnecting"
        @unknown default: return "state \(state.rawValue)"
        }
    }
}
