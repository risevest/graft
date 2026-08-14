import Foundation

public class GraftPreferences: NSObject {
    private let blockedBundleIdsKey = "blockedBundleIds"
    private let channelKey = "channel"
    private let highestInstalledCounterKey = "highestInstalledCounter"
    private let installIdKey = "installId"
    private let lastKnownGoodBundleIdKey = "lastKnownGoodBundleId"
    private let lastNativeBuildKey = "lastNativeBuild"
    private let previousBundleIdKey = "previousBundleId"

    public func getBlockedBundleIds() -> String? {
        return UserDefaults.standard.string(forKey: applyPrefix(to: blockedBundleIdsKey))
    }

    public func getChannel() -> String? {
        return UserDefaults.standard.string(forKey: applyPrefix(to: channelKey))
    }

    public func getHighestInstalledCounter() -> Int {
        return UserDefaults.standard.integer(forKey: applyPrefix(to: highestInstalledCounterKey))
    }

    /// A random identifier, created on first use, that fixes this install's rollout bucket.
    public func getInstallId() -> String {
        let key = applyPrefix(to: installIdKey)
        if let installId = UserDefaults.standard.string(forKey: key) {
            return installId
        }
        let installId = UUID().uuidString.lowercased()
        UserDefaults.standard.set(installId, forKey: key)
        return installId
    }

    public func getLastKnownGoodBundleId() -> String? {
        return UserDefaults.standard.string(forKey: applyPrefix(to: lastKnownGoodBundleIdKey))
    }

    /// - Returns: The build the pointer was last reconciled against, or `nil` on a fresh install.
    public func getLastNativeBuild() -> Int? {
        return UserDefaults.standard.object(forKey: applyPrefix(to: lastNativeBuildKey)) as? Int
    }

    public func getPreviousBundleId() -> String? {
        return UserDefaults.standard.string(forKey: applyPrefix(to: previousBundleIdKey))
    }

    public func setBlockedBundleIds(_ value: String?) {
        setString(value, forKey: blockedBundleIdsKey)
    }

    public func setChannel(_ value: String?) {
        setString(value, forKey: channelKey)
    }

    public func setHighestInstalledCounter(_ value: Int) {
        UserDefaults.standard.set(value, forKey: applyPrefix(to: highestInstalledCounterKey))
    }

    public func setLastKnownGoodBundleId(_ value: String?) {
        setString(value, forKey: lastKnownGoodBundleIdKey)
    }

    public func setLastNativeBuild(_ value: Int) {
        UserDefaults.standard.set(value, forKey: applyPrefix(to: lastNativeBuildKey))
    }

    public func setPreviousBundleId(_ value: String?) {
        setString(value, forKey: previousBundleIdKey)
    }

    private func setString(_ value: String?, forKey key: String) {
        if let value = value {
            UserDefaults.standard.set(value, forKey: applyPrefix(to: key))
        } else {
            UserDefaults.standard.removeObject(forKey: applyPrefix(to: key))
        }
    }

    private func applyPrefix(to key: String) -> String {
        return GraftPlugin.userDefaultsPrefix + "." + key
    }
}
