import ExternalAccessory
import Foundation
import BlazbudsKit

/// A control link to earbuds that iOS treats as an accessory (Sony, Bose): a session on one of the protocols the app
/// declares. Its streams run on the main run loop; they only ever read what has arrived and write what fits, so
/// nothing waits there, and the messages are a few bytes.
final class AccessoryLink: NSObject, BudsWire, StreamDelegate {
    private let session: EASession
    private let pipe: BudsPipe
    private var outbox = Data()
    private var announced = false
    private var closed = false

    /// The links open now: a stream doesn't keep its delegate alive. Touched on the main thread only.
    private static var live: [ObjectIdentifier: AccessoryLink] = [:]

    private init(session: EASession, pipe: BudsPipe) {
        self.session = session
        self.pipe = pipe
    }

    /// Opens a session to the accessory "ea:<connection id>" on [route], or on the first known protocol it speaks.
    static func open(id: String, route: String, pipe: BudsPipe) {
        DispatchQueue.main.async {
            let number = Int(id.dropFirst(3)) ?? -1
            guard let accessory = EAAccessoryManager.shared().connectedAccessories.first(where: { $0.connectionID == number }) else {
                pipe.failed(reason: "These earbuds aren't connected now", busy: false)
                return
            }
            let wanted = route.isEmpty ? Accessories.known : [route]
            guard let name = wanted.first(where: { accessory.protocolStrings.contains($0) }) else {
                pipe.failed(reason: "These earbuds don't offer \(route.isEmpty ? "a known protocol" : route)", busy: false)
                return
            }
            guard let session = EASession(accessory: accessory, forProtocol: name) else {
                // Inside another app (LiveContainer) only that app's declared protocols open.
                pipe.failed(reason: "iOS didn't open \(name). Inside LiveContainer accessories can't be reached; Blazbuds needs to be installed as its own app for them", busy: false)
                return
            }
            BudsLog.shared.add(line: "iPhone accessory: \(accessory.name) on \(name)")
            let link = AccessoryLink(session: session, pipe: pipe)
            live[ObjectIdentifier(link)] = link
            link.start()
        }
    }

    private var streams: [Stream] { [session.inputStream, session.outputStream].compactMap { $0 } }

    private func start() {
        for stream in streams {
            stream.delegate = self
            stream.schedule(in: .main, forMode: .default)
            stream.open()
        }
    }

    // MARK: BudsWire

    func send(data: Data) {
        DispatchQueue.main.async { [self] in
            guard !closed else { return }
            outbox.append(data)
            flush()
        }
    }

    func close() {
        DispatchQueue.main.async { [self] in finish() }
    }

    // MARK: Streams, on the main thread

    private func finish() {
        guard !closed else { return }
        closed = true
        for stream in streams {
            stream.close()
            stream.remove(from: .main, forMode: .default)
            stream.delegate = nil
        }
        AccessoryLink.live[ObjectIdentifier(self)] = nil
    }

    private func flush() {
        guard !outbox.isEmpty, let output = session.outputStream, output.hasSpaceAvailable else { return }
        let pending = [UInt8](outbox)
        let written = output.write(pending, maxLength: pending.count)
        if written > 0 { outbox.removeFirst(min(written, outbox.count)) }
    }

    func stream(_ aStream: Stream, handle eventCode: Stream.Event) {
        switch eventCode {
        case .openCompleted:
            if aStream === session.outputStream && !announced {
                announced = true
                pipe.opened(wire: self)
            }
        case .hasBytesAvailable:
            guard let input = session.inputStream else { return }
            var buffer = [UInt8](repeating: 0, count: 1024)
            while input.hasBytesAvailable {
                let count = input.read(&buffer, maxLength: buffer.count)
                if count <= 0 { break }
                pipe.received(data: Data(buffer[0 ..< count]))
            }
        case .hasSpaceAvailable:
            flush()
        case .errorOccurred, .endEncountered:
            BudsLog.shared.add(line: "iPhone accessory: the session ended (\(aStream.streamError?.localizedDescription ?? "closed"))")
            if announced { pipe.ended() } else { pipe.failed(reason: "The accessory session closed", busy: false) }
            announced = true
            finish()
        default:
            break
        }
    }
}
