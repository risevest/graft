import Foundation
import Capacitor

@objc(GraftPlugin)
public class GraftPlugin: CAPPlugin, CAPBridgedPlugin {
    public static let tag = "Graft"
    public static let userDefaultsPrefix = "RisemaxiGraft" // DO NOT CHANGE

    public let identifier = "GraftPlugin"
    public let jsName = "Graft"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "clearBlockedBundles", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "deleteBundle", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "downloadBundle", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getBlockedBundles", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getChannel", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getCurrentBundle", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getDownloadedBundles", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getInstallId", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getNextBundle", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getVersionCode", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getVersionName", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "isSyncing", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "ready", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "reload", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "reset", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setChannel", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setNextBundle", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "sync", returnType: CAPPluginReturnPromise)
    ]

    private let eventDownloadBundleProgess = "downloadBundleProgress"
    private let eventNextBundleSet = "nextBundleSet"
    private let eventReloaded = "reloaded"

    private var implementation: Graft?

    override public func load() {
        let implementation = Graft(config: graftConfig(), plugin: self)
        self.implementation = implementation

        implementation.handleLoad()

        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleAppWillEnterForeground),
            name: UIApplication.willEnterForegroundNotification,
            object: nil
        )
    }

    @objc func clearBlockedBundles(_ call: CAPPluginCall) {
        guard let implementation = graft(call) else {
            return
        }
        implementation.clearBlockedBundles()
        call.resolve()
    }

    @objc func deleteBundle(_ call: CAPPluginCall) {
        guard let bundleId = call.getString("bundleId") else {
            call.reject(CustomError.bundleIdMissing.localizedDescription)
            return
        }
        graft(call)?.deleteBundle(DeleteBundleOptions(bundleId: bundleId), completion: emptyCompletion(call))
    }

    @objc func downloadBundle(_ call: CAPPluginCall) {
        guard let implementation = graft(call) else {
            return
        }
        guard let bundleId = call.getString("bundleId") else {
            call.reject(CustomError.bundleIdMissing.localizedDescription)
            return
        }
        guard let checksum = call.getString("checksum") else {
            call.reject(CustomError.checksumMissing.localizedDescription)
            return
        }
        guard let url = call.getString("url") else {
            call.reject(CustomError.urlMissing.localizedDescription)
            return
        }
        Task {
            do {
                try await implementation.downloadBundle(DownloadBundleOptions(bundleId: bundleId, checksum: checksum, url: url))
                call.resolve()
            } catch {
                rejectCall(call, error)
            }
        }
    }

    @objc func getBlockedBundles(_ call: CAPPluginCall) {
        graft(call)?.getBlockedBundles(completion: resultCompletion(call))
    }

    @objc func getChannel(_ call: CAPPluginCall) {
        graft(call)?.getChannel(completion: resultCompletion(call))
    }

    @objc func getCurrentBundle(_ call: CAPPluginCall) {
        graft(call)?.getCurrentBundle(completion: resultCompletion(call))
    }

    @objc func getDownloadedBundles(_ call: CAPPluginCall) {
        graft(call)?.getDownloadedBundles(completion: resultCompletion(call))
    }

    @objc func getInstallId(_ call: CAPPluginCall) {
        graft(call)?.getInstallId(completion: resultCompletion(call))
    }

    @objc func getNextBundle(_ call: CAPPluginCall) {
        graft(call)?.getNextBundle(completion: resultCompletion(call))
    }

    @objc func getVersionCode(_ call: CAPPluginCall) {
        graft(call)?.getVersionCode(completion: resultCompletion(call))
    }

    @objc func getVersionName(_ call: CAPPluginCall) {
        graft(call)?.getVersionName(completion: resultCompletion(call))
    }

    @objc func isSyncing(_ call: CAPPluginCall) {
        graft(call)?.isSyncing(completion: resultCompletion(call))
    }

    @objc func ready(_ call: CAPPluginCall) {
        graft(call)?.ready(completion: resultCompletion(call))
    }

    @objc func reload(_ call: CAPPluginCall) {
        guard let implementation = graft(call) else {
            return
        }
        implementation.reload()
        call.resolve()
    }

    @objc func reset(_ call: CAPPluginCall) {
        guard let implementation = graft(call) else {
            return
        }
        implementation.reset()
        call.resolve()
    }

    @objc func setChannel(_ call: CAPPluginCall) {
        graft(call)?.setChannel(SetChannelOptions(channel: call.getString("channel")), completion: emptyCompletion(call))
    }

    @objc func setNextBundle(_ call: CAPPluginCall) {
        graft(call)?.setNextBundle(SetNextBundleOptions(call), completion: emptyCompletion(call))
    }

    @objc func sync(_ call: CAPPluginCall) {
        guard let implementation = graft(call) else {
            return
        }
        Task {
            do {
                resolveCall(call, try await implementation.sync(SyncOptions(call)))
            } catch {
                rejectCall(call, error)
            }
        }
    }

    func notifyDownloadBundleProgressListeners(_ event: DownloadBundleProgressEvent) {
        if let event = event.toJSObject() as? JSObject {
            notifyListeners(eventDownloadBundleProgess, data: event, retainUntilConsumed: false)
        }
    }

    func notifyNextBundleSetListeners(_ event: NextBundleSetEvent) {
        notifyListeners(eventNextBundleSet, data: event.toJSObject(), retainUntilConsumed: false)
    }

    func notifyReloadedListeners() {
        notifyListeners(eventReloaded, data: JSObject(), retainUntilConsumed: true)
    }

    @objc private func handleAppWillEnterForeground() {
        implementation?.handleAppWillEnterForeground()
    }

    private func graftConfig() -> GraftConfig {
        var config = GraftConfig()

        config.autoBlockRolledBackBundles = getConfig().getBoolean("autoBlockRolledBackBundles", config.autoBlockRolledBackBundles)
        config.autoDeleteBundles = getConfig().getBoolean("autoDeleteBundles", config.autoDeleteBundles)
        config.autoUpdateStrategy = getConfig().getString("autoUpdateStrategy", config.autoUpdateStrategy) ?? config.autoUpdateStrategy
        config.defaultChannel = getConfig().getString("defaultChannel", config.defaultChannel)
        config.httpTimeout = getConfig().getInt("httpTimeout", config.httpTimeout)
        config.publicKey = getConfig().getString("publicKey", config.publicKey)
        config.readyTimeout = getConfig().getInt("readyTimeout", config.readyTimeout)
        config.serverUrl = getConfig().getString("serverUrl", config.serverUrl)

        return config
    }

    private func graft(_ call: CAPPluginCall) -> Graft? {
        if implementation == nil {
            call.reject(CustomError.notInitialized.localizedDescription)
        }
        return implementation
    }

    private func emptyCompletion(_ call: CAPPluginCall) -> (Error?) -> Void {
        return { error in
            if let error = error {
                self.rejectCall(call, error)
                return
            }
            call.resolve()
        }
    }

    private func resultCompletion(_ call: CAPPluginCall) -> (Result?, Error?) -> Void {
        return { result, error in
            if let error = error {
                self.rejectCall(call, error)
                return
            }
            guard let result = result else {
                call.resolve()
                return
            }
            self.resolveCall(call, result)
        }
    }

    private func resolveCall(_ call: CAPPluginCall, _ result: Result) {
        guard let value = result.toJSObject() as? JSObject else {
            call.resolve()
            return
        }
        call.resolve(value)
    }

    private func rejectCall(_ call: CAPPluginCall, _ error: Error) {
        CAPLog.print("[", GraftPlugin.tag, "] ", error)
        var message = error.localizedDescription
        if let urlError = error as? URLError, urlError.code == .timedOut {
            message = CustomError.httpTimeout.localizedDescription
        }
        call.reject(message)
    }
}
