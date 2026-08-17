import Foundation
import Capacitor

@objc public class GetInstallIdResult: NSObject, Result {
    let bucket: Int
    let installId: String

    init(installId: String, bucket: Int) {
        self.installId = installId
        self.bucket = bucket
    }

    public func toJSObject() -> AnyObject {
        var result = JSObject()
        result["bucket"] = bucket
        result["installId"] = installId
        return result as AnyObject
    }
}
