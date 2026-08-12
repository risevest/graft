import Foundation

public enum GraftPointer {
    private static let activeBundleIdKey = GraftPlugin.userDefaultsPrefix + ".activeBundleId"
    private static let bundlesDirectory = "graft/bundles"
    private static let embeddedWebAssetDir = "public" // DO NOT CHANGE! (See https://dub.sh/Buvz4yj)
    private static let indexFileName = "index.html"

    public static func resolveActiveBundleDirectory() -> URL {
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

    private static var applicationSupportDirectory: URL {
        return FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
    }
}
