import CoreBluetooth
import Foundation
import BlazbudsKit

/// One control link over Bluetooth LE: it connects, finds the earbuds' control service, listens on its notify
/// characteristic, and writes the drivers' messages in pieces the link takes. All of it runs on the owner's queue.
final class BleLink: NSObject, BudsWire, CBPeripheralDelegate {
    let peripheral: CBPeripheral
    private let pipe: BudsPipe
    private weak var owner: Bluetooth?

    private var writer: CBCharacteristic?
    private var reader: CBCharacteristic?
    private var writeType: CBCharacteristicWriteType = .withoutResponse
    private var outbox: [Data] = []
    private var waitingForWrite = false
    private var opened = false
    private var done = false
    private var replies = 0
    /// Services still being looked inside for Soundcore's 7777 and 8888, when none was a known control service.
    private var searching = 0

    /// The route the control service belongs to, once found.
    private(set) var route = ""

    /// What was chosen and the first replies, for the Bluetooth check.
    private(set) var summary: [String] = []

    init(peripheral: CBPeripheral, pipe: BudsPipe, owner: Bluetooth) {
        self.peripheral = peripheral
        self.pipe = pipe
        self.owner = owner
    }

    private var name: String { "\(peripheral.name ?? "earbuds") [\(peripheral.identifier.uuidString.prefix(8))]" }

    private func note(_ line: String) {
        summary.append(line)
        BudsLog.shared.add(line: "iPhone link: \(line)")
    }

    func start() {
        peripheral.delegate = self
        if peripheral.state == .connected {
            note("\(name) is connected to the app already")
            connected()
        } else {
            // For earbuds out of reach iOS keeps this waiting, and it completes once they come back.
            note("connecting \(name)")
            owner?.central.connect(peripheral, options: nil)
        }
    }

    func connected() {
        guard !done else { return }
        note("connected \(name); listing its services")
        peripheral.discoverServices(nil)
    }

    /// Opening failed: the driver hears why and tries again later.
    func fail(_ reason: String, busy: Bool = false) {
        guard !done else { return }
        done = true
        note(reason)
        if opened { pipe.ended() } else { pipe.failed(reason: reason, busy: busy) }
        owner?.forget(self)
    }

    func disconnected(_ error: Error?) {
        guard !done else { return }
        note("\(name) disconnected\(error.map { ": \($0.localizedDescription)" } ?? "")")
        done = true
        if opened { pipe.ended() } else { pipe.failed(reason: "The earbuds disconnected", busy: false) }
        owner?.forget(self)
    }

    /// A newer link to the same earbuds takes over.
    func retire() {
        guard !done else { return }
        done = true
        if opened { pipe.ended() } else { pipe.failed(reason: "Opened again", busy: false) }
    }

    // MARK: BudsWire

    func send(data: Data) {
        owner?.queue.async { [self] in
            guard !done, let writer = writer else { return }
            let size = max(20, peripheral.maximumWriteValueLength(for: writeType))
            var at = 0
            while at < data.count {
                outbox.append(data.subdata(in: at ..< min(at + size, data.count)))
                at += size
            }
            pump(writer)
        }
    }

    func close() {
        owner?.queue.async { [self] in
            guard !done else { return }
            done = true
            // The app's connection stays: the earbuds are connected for music anyway, and reopening is quicker.
            if let reader = reader, reader.isNotifying { peripheral.setNotifyValue(false, for: reader) }
            if peripheral.delegate === self { peripheral.delegate = nil }
            owner?.forget(self)
        }
    }

    private func pump(_ writer: CBCharacteristic) {
        while !outbox.isEmpty {
            if writeType == .withoutResponse {
                // Resumed by peripheralIsReady(toSendWriteWithoutResponse:).
                guard peripheral.canSendWriteWithoutResponse else { return }
                peripheral.writeValue(outbox.removeFirst(), for: writer, type: .withoutResponse)
            } else {
                // Resumed by didWriteValueFor.
                guard !waitingForWrite else { return }
                waitingForWrite = true
                peripheral.writeValue(outbox.removeFirst(), for: writer, type: .withResponse)
            }
        }
    }

    // MARK: CBPeripheralDelegate

    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard !done else { return }
        if let error = error {
            fail("Couldn't list the services of \(name): \(error.localizedDescription)")
            return
        }
        let services = peripheral.services ?? []
        let list = services.map { Routes.full($0.uuid) }.joined(separator: ", ")
        note("services: \(list.isEmpty ? "none" : list)")
        guard let picked = Routes.pick(services) else {
            guard !services.isEmpty else {
                fail("\(name) offers no services")
                return
            }
            // None is a known control service (either way round): look inside each for Soundcore's parts.
            note("no known control service; looking inside each for write 7777 and notify 8888")
            searching = services.count
            for service in services { peripheral.discoverCharacteristics(nil, for: service) }
            return
        }
        route = picked.route
        note("\(picked.route) control service \(Routes.full(picked.service.uuid))")
        peripheral.discoverCharacteristics(nil, for: picked.service)
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        // Once the parts are chosen, answers for the other services looked inside don't matter.
        guard !done, writer == nil else { return }
        if searching > 0 {
            searching -= 1
            if writer == nil && error == nil && Routes.soundcoreParts(service) {
                searching = 0
                route = Routes.soundcore.name
                note("Soundcore's parts are in service \(Routes.full(service.uuid))")
                use(service)
            } else if searching == 0 && writer == nil {
                fail("\(name) has no control service the app knows, nor Soundcore's 7777 and 8888")
            }
            return
        }
        if let error = error {
            fail("Couldn't list the control service's parts: \(error.localizedDescription)")
            return
        }
        use(service)
    }

    /// Takes the write and notify parts of [service] and starts listening.
    private func use(_ service: CBService) {
        let all = service.characteristics ?? []
        guard let pair = Routes.pickCharacteristics(all) else {
            let list = all.map { "\(Routes.full($0.uuid)) [\(Routes.describe($0.properties))]" }.joined(separator: ", ")
            fail("The control service has nothing to write to and listen on (\(list))")
            return
        }
        let write = pair.write
        let notify = pair.notify
        writer = write
        reader = notify
        writeType = write.properties.contains(.writeWithoutResponse) ? .withoutResponse : .withResponse
        note("write \(Routes.full(write.uuid)) [\(Routes.describe(write.properties))], listen \(Routes.full(notify.uuid)) [\(Routes.describe(notify.properties))], pieces of \(peripheral.maximumWriteValueLength(for: writeType)) bytes")
        if notify.isNotifying {
            ready()
        } else {
            peripheral.setNotifyValue(true, for: notify)
        }
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateNotificationStateFor characteristic: CBCharacteristic, error: Error?) {
        guard !done, characteristic === reader else { return }
        if let error = error {
            fail("The earbuds didn't let the app listen: \(error.localizedDescription)")
            return
        }
        if characteristic.isNotifying {
            note("listening; the link is open")
            ready()
        }
    }

    private func ready() {
        guard !opened else { return }
        opened = true
        pipe.opened(wire: self)
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        guard !done, characteristic === reader, error == nil, let value = characteristic.value, !value.isEmpty else { return }
        if replies < 3 {
            replies += 1
            note("reply \(replies): \(Routes.hex(value, limit: 24))")
            // The earbuds answered: found again by this id from now on, even before they are connected.
            if replies == 1 { owner?.remember(peripheral, route: route.isEmpty ? Routes.soundcore.name : route) }
        }
        pipe.received(data: value)
    }

    func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {
        waitingForWrite = false
        if let error = error { BudsLog.shared.add(line: "iPhone link: a write failed: \(error.localizedDescription)") }
        if let writer = writer, !done { pump(writer) }
    }

    func peripheralIsReady(toSendWriteWithoutResponse peripheral: CBPeripheral) {
        if let writer = writer, !done { pump(writer) }
    }
}
