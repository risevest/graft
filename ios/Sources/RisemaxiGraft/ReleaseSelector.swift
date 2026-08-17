import Foundation

/// Decides which release of a channel this install should move to. Kept free of platform state so it
/// stays identical to its Android counterpart.
public enum ReleaseSelector {
    /// A value in 0..99 that stays fixed for the lifetime of the install, so a device keeps its
    /// position as a rollout widens.
    public static func bucket(for installId: String) -> Int {
        var hash: UInt32 = 0x811c_9dc5
        for byte in Array(installId.utf8) {
            hash ^= UInt32(byte)
            hash = hash &* 0x0100_0193
        }
        return Int(hash % 100)
    }

    /// The highest-counter release this install is eligible for, or `nil` when it is already on the
    /// newest release it can run.
    public static func select(
        from releases: [ChannelRelease],
        versionCode: Int,
        highestInstalledCounter: Int,
        bucket: Int,
        blockedBundleIds: Set<String>
    ) -> ChannelRelease? {
        return releases
            .filter {
                $0.minNativeBuild <= versionCode
                    && $0.counter > highestInstalledCounter
                    && $0.rollout > bucket
                    && !blockedBundleIds.contains($0.id)
            }
            .max { $0.counter < $1.counter }
    }
}
