// A stand-in for the little of SwiftUI the iPhone app uses: an App with one window hosting a UIKit controller.
@_exported import Foundation
@_exported import UIKit

public protocol View {
    associatedtype Body: View
    @ViewBuilder var body: Body { get }
}

extension Never: View {
    public typealias Body = Never
    public var body: Never { fatalError() }
}

public struct _Modified<Content>: View {
    public typealias Body = Never
    public var body: Never { fatalError() }
    init(_ content: Content) {}
}

@resultBuilder public struct ViewBuilder {
    public static func buildBlock<Content: View>(_ content: Content) -> Content { content }
}

public struct SafeAreaRegions: OptionSet {
    public let rawValue: UInt
    public init(rawValue: UInt) { self.rawValue = rawValue }
    public static let all = SafeAreaRegions(rawValue: 7)
}

public enum Edge {
    public struct Set: OptionSet {
        public let rawValue: Int8
        public init(rawValue: Int8) { self.rawValue = rawValue }
        public static let all = Set(rawValue: 15)
    }
}

extension View {
    public func ignoresSafeArea(_ regions: SafeAreaRegions = .all, edges: Edge.Set = .all) -> some View { _Modified(self) }
}

@MainActor @preconcurrency public protocol App {
    associatedtype Body: Scene
    @SceneBuilder @MainActor var body: Body { get }
    init()
}

extension App {
    @MainActor public static func main() {}
}

@MainActor @preconcurrency public protocol Scene {
    associatedtype Body: Scene
    @SceneBuilder @MainActor var body: Body { get }
}

extension Never: Scene {}

@resultBuilder public struct SceneBuilder {
    public static func buildBlock<Content: Scene>(_ content: Content) -> Content { content }
}

public struct WindowGroup<Content: View>: Scene {
    public typealias Body = Never
    public var body: Never { fatalError() }
    public init(@ViewBuilder content: () -> Content) {}
}

@MainActor @preconcurrency public protocol UIViewControllerRepresentable: View where Body == Never {
    associatedtype UIViewControllerType: UIViewController
    associatedtype Coordinator = Void
    typealias Context = UIViewControllerRepresentableContext<Self>
    func makeUIViewController(context: Context) -> UIViewControllerType
    func updateUIViewController(_ uiViewController: UIViewControllerType, context: Context)
    func makeCoordinator() -> Coordinator
}

extension UIViewControllerRepresentable {
    public var body: Never { fatalError() }
}

extension UIViewControllerRepresentable where Coordinator == Void {
    public func makeCoordinator() -> Coordinator { () }
}

public struct UIViewControllerRepresentableContext<Representable: UIViewControllerRepresentable> {
    public let coordinator: Representable.Coordinator
}
