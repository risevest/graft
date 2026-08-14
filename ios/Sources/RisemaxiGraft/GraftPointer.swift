import Foundation
import Capacitor

/// Owns the on-disk bundle layout and the pointer at the bundle to serve. Everything here runs before
/// the WebView exists, so it must stay synchronous and cheap.
public enum GraftPointer {
    private static let activeBundleIdKey = GraftPlugin.userDefaultsPrefix + ".activeBundleId"
    private static let bundlesDirectory = "graft/bundles"

    static let embeddedWebAssetDir = "public" // DO NOT CHANGE! (See https://dub.sh/Buvz4yj)
    static let indexFileName = "index.html"
    static let manifestFileName = "graft-manifest.json"

    public static func resolveActiveBundleDirectory() -> URL {
        applyBinaryChangeRetention()
        guard let bundleId = getActiveBundleId() else {
            return buildEmbeddedBundleDirectory()
        }
        let directory = buildBundleDirectory(bundleId: bundleId)
        guard FileManager.default.fileExists(atPath: directory.appendingPathComponent(indexFileName).path) else {
            return buildEmbeddedBundleDirectory()
        }
        return directory
    }

    public static func buildEmbeddedBundleDirectory() -> URL {
        return Bundle.main.bundleURL.appendingPathComponent(embeddedWebAssetDir)
    }

    public static func getActiveBundleId() -> String? {
        return UserDefaults.standard.string(forKey: activeBundleIdKey)
    }

    public static func setActiveBundleId(_ bundleId: String) {
        UserDefaults.standard.set(bundleId, forKey: activeBundleIdKey)
    }

    public static func clearActiveBundleId() {
        UserDefaults.standard.removeObject(forKey: activeBundleIdKey)
    }

    public static func buildBundlesDirectory() -> URL {
        return applicationSupportDirectory.appendingPathComponent(bundlesDirectory)
    }

    public static func buildBundleDirectory(bundleId: String) -> URL {
        return buildBundlesDirectory().appendingPathComponent(bundleId)
    }

    static func readManifest(at url: URL) -> Manifest? {
        guard let data = try? Data(contentsOf: url) else {
            return nil
        }
        do {
            return try JSONDecoder().decode(Manifest.self, from: data)
        } catch {
            CAPLog.print("[", GraftPlugin.tag, "] ", "Failed to read manifest at \(url.path): \(error)")
            return nil
        }
    }

    static func readEmbeddedManifest() -> Manifest? {
        return readManifest(at: buildEmbeddedBundleDirectory().appendingPathComponent(manifestFileName))
    }

    static func readNativeBuild() -> Int? {
        guard let value = Bundle.main.infoDictionary?["CFBundleVersion"] as? String else {
            return nil
        }
        return Int(value)
    }

    /// Capacitor discards its own `appLocation` whenever the binary changes, which would silently
    /// downgrade a device to whatever the store build embeds. We read our own pointer instead, so the
    /// same decision is made here — but only for bundles the new binary genuinely cannot serve.
    private static func applyBinaryChangeRetention() {
        guard let nativeBuild = readNativeBuild() else {
            return
        }
        let preferences = GraftPreferences()
        if preferences.getLastNativeBuild() == nativeBuild {
            return
        }

        let embeddedManifest = readEmbeddedManifest()
        if let bundleId = getActiveBundleId(), !isBundleRunnable(bundleId, nativeBuild: nativeBuild, embeddedManifest: embeddedManifest) {
            CAPLog.print("[", GraftPlugin.tag, "] ", "Discarding bundle \(bundleId) on native build \(nativeBuild).")
            clearActiveBundleId()
        }
        if let bundleId = preferences.getLastKnownGoodBundleId(),
           !isBundleRunnable(bundleId, nativeBuild: nativeBuild, embeddedManifest: embeddedManifest) {
            preferences.setLastKnownGoodBundleId(nil)
        }
        if let embeddedManifest = embeddedManifest, embeddedManifest.counter > preferences.getHighestInstalledCounter() {
            preferences.setHighestInstalledCounter(embeddedManifest.counter)
        }
        preferences.setLastNativeBuild(nativeBuild)
    }

    private static func isBundleRunnable(_ bundleId: String, nativeBuild: Int, embeddedManifest: Manifest?) -> Bool {
        guard let manifest = readManifest(at: buildBundleDirectory(bundleId: bundleId).appendingPathComponent(manifestFileName)) else {
            return false
        }
        if manifest.minNativeBuild > nativeBuild {
            return false
        }
        return embeddedManifest == nil || embeddedManifest!.counter <= manifest.counter
    }

    private static var applicationSupportDirectory: URL {
        return FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
    }
}
