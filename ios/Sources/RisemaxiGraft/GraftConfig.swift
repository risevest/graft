public struct GraftConfig {
    var autoBlockRolledBackBundles = true
    var autoDeleteBundles = true
    var autoUpdateStrategy = "none"
    var defaultChannel: String?
    var httpTimeout = 60000
    var publicKey: String?
    var readyTimeout = 10000
    var serverUrl: String?
}
