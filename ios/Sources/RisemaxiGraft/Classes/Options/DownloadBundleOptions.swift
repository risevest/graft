import Foundation
import Capacitor

@objc public class DownloadBundleOptions: NSObject {
    private let bundleId: String
    private let checksum: String
    private let url: String

    init(bundleId: String, checksum: String, url: String) {
        self.bundleId = bundleId
        self.checksum = checksum
        self.url = url
    }

    func getBundleId() -> String {
        return bundleId
    }

    func getChecksum() -> String {
        return checksum
    }

    func getUrl() -> String {
        return url
    }
}
