import Foundation

public class GraftPreferences: NSObject {
    private let blockedBundleIdsKey = "blockedBundleIds"
    private let channelEtagKey = "channelEtag"
    private let channelKey = "channel"
    private let highestInstalledCounterKey = "highestInstalledCounter"
    private let installIdKey = "installId"
    private let lastFailedBundleIdKey = "lastFailedBundleId"
    private let lastFailedCountKey = "lastFailedCount"
    private let lastKnownGoodBundleIdKey = "lastKnownGoodBundleId"
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

    public func getLastFailedBundleId() -> String? {
        return UserDefaults.standard.string(forKey: applyPrefix(to: lastFailedBundleIdKey))
    }

    public func getLastFailedCount() -> Int {
        return UserDefaults.standard.integer(forKey: applyPrefix(to: lastFailedCountKey))
    }

    public func setLastFailed(_ bundleId: String?, count: Int) {
        setString(bundleId, forKey: lastFailedBundleIdKey)
        if bundleId == nil {
            UserDefaults.standard.removeObject(forKey: applyPrefix(to: lastFailedCountKey))
        } else {
            UserDefaults.standard.set(count, forKey: applyPrefix(to: lastFailedCountKey))
        }
    }

    /// The tag is only worth sending while the conclusion drawn from that document still holds, and
    /// that conclusion depends on which binary asked. Storing the two together makes a tag recorded
    /// under a previous binary simply not match, so the document is re-read after a store update.
    public func getChannelEtag(nativeFingerprint: String) -> String? {
        guard let stored = UserDefaults.standard.object(forKey: applyPrefix(to: channelEtagKey)) as? String,
              let separator = stored.firstIndex(of: "\n"),
              stored[stored.startIndex..<separator] == nativeFingerprint else {
            return nil
        }
        return String(stored[stored.index(after: separator)...])
    }

    public func setChannelEtag(_ value: String?, nativeFingerprint: String) {
        if let value = value {
            UserDefaults.standard.set("\(nativeFingerprint)\n\(value)", forKey: applyPrefix(to: channelEtagKey))
        } else {
            UserDefaults.standard.removeObject(forKey: applyPrefix(to: channelEtagKey))
        }
    }

    public func getLastKnownGoodBundleId() -> String? {
        return UserDefaults.standard.string(forKey: applyPrefix(to: lastKnownGoodBundleIdKey))
    }

    /// - Returns: The build the pointer was last reconciled against, or `nil` on a fresh install.
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
