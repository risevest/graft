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

    /// Set when a host asks where to serve from, which is the only moment the pointer can take
    /// effect. Graft's own reads deliberately bypass this, so it records the host wiring and nothing
    /// else.
    private(set) static var wasAskedForBundleDirectory = false

    public static func resolveActiveBundleDirectory() -> URL {
        wasAskedForBundleDirectory = true
        return activeBundleDirectory()
    }

    private static func activeBundleDirectory() -> URL {
        discardBundlesThisBinaryCannotServe()
        guard let bundleId = getActiveBundleId() else {
            return buildEmbeddedBundleDirectory()
        }
        let directory = buildBundleDirectory(bundleId: bundleId)
        guard FileManager.default.fileExists(atPath: directory.appendingPathComponent(indexFileName).path) else {
            return buildEmbeddedBundleDirectory()
        }
        return directory
    }

    /// The id of the bundle this launch resolved, or `nil` for the embedded one. Unlike the bridge's
    /// server path this is available before the WebView exists.
    public static func resolveActiveBundleId() -> String? {
        let name = activeBundleDirectory().lastPathComponent
        return name == embeddedWebAssetDir ? nil : name
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
    /// downgrade a device to whatever the store build embeds. We keep our own pointer instead and drop
    /// only the bundles this binary genuinely cannot serve.
    ///
    /// Runs on every launch rather than only when the binary changed. The comparison it makes is
    /// against the bundle on disk, so it needs no memory of previous launches, and reaching the same
    /// answer costs two manifest reads — hundredths of a millisecond against a file already in page
    /// cache. Remembering instead would mean storing a fact whose source of truth is that same file.
    private static func discardBundlesThisBinaryCannotServe() {
        let embeddedManifest = readEmbeddedManifest()
        guard let fingerprint = embeddedManifest?.nativeFingerprint else {
            CAPLog.print("[", GraftPlugin.tag, "] ", "The embedded manifest carries no native fingerprint, so a staged bundle cannot be reconciled.")
            return
        }
        let preferences = GraftPreferences()
        if let bundleId = getActiveBundleId(), !isBundleRunnable(bundleId, nativeFingerprint: fingerprint, embeddedManifest: embeddedManifest) {
            CAPLog.print("[", GraftPlugin.tag, "] ", "Discarding bundle \(bundleId): it was built against different native code.")
            clearActiveBundleId()
        }
        if let bundleId = preferences.getLastKnownGoodBundleId(),
           !isBundleRunnable(bundleId, nativeFingerprint: fingerprint, embeddedManifest: embeddedManifest) {
            preferences.setLastKnownGoodBundleId(nil)
        }
        if let embeddedCounter = embeddedManifest?.counter, embeddedCounter > preferences.getHighestInstalledCounter() {
            preferences.setHighestInstalledCounter(embeddedCounter)
        }
    }

    private static func isBundleRunnable(_ bundleId: String, nativeFingerprint: String, embeddedManifest: Manifest?) -> Bool {
        guard let manifest = readManifest(at: buildBundleDirectory(bundleId: bundleId).appendingPathComponent(manifestFileName)) else {
            return false
        }
        if manifest.nativeFingerprint != nativeFingerprint {
            return false
        }
        guard let embeddedCounter = embeddedManifest?.counter, let counter = manifest.counter else {
            return true
        }
        return embeddedCounter <= counter
    }

    private static var applicationSupportDirectory: URL {
        return FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
    }
}
