import Foundation
import Capacitor
import Alamofire

public class GraftHttpClient: NSObject {

    private let config: GraftConfig

    init(config: GraftConfig) {
        self.config = config
    }

    /// A GET that may answer 304. `data` is nil exactly when the server says nothing changed, in
    /// which case the caller's stored tag is still current.
    public struct ConditionalData {
        public let data: Data?
        public let etag: String?
    }

    public func conditionalData(url: URL, etag: String?) async throws -> ConditionalData {
        var request = buildRequest(url: url)
        if let etag = etag {
            request.setValue(etag, forHTTPHeaderField: "If-None-Match")
        }
        let response = await AF.request(request)
            .validate(statusCode: [200, 304])
            .serializingData(emptyResponseCodes: [204, 205, 304])
            .response
        if let error = response.error {
            throw unwrap(error)
        }
        let served = response.response?.value(forHTTPHeaderField: "Etag")
        if response.response?.statusCode == 304 {
            return ConditionalData(data: nil, etag: etag)
        }
        guard let data = response.value else {
            throw CustomError.downloadFailed
        }
        return ConditionalData(data: data, etag: served)
    }

    public func data(url: URL) async throws -> Data {
        let response = await AF.request(buildRequest(url: url)).validate().serializingData().response
        if let error = response.error {
            throw unwrap(error)
        }
        guard let data = response.value else {
            throw CustomError.downloadFailed
        }
        return data
    }

    public func download(url: URL, to file: URL, callback: ((Progress) -> Void)?) async throws {
        let destination: DownloadRequest.Destination = { _, _ in
            // `removePreviousFile` ensures that a leftover file from a failed attempt does not fail the download
            return (file, [.createIntermediateDirectories, .removePreviousFile])
        }
        // `validate()` treats non-2xx responses as errors so that an error body is never saved as a bundle file
        let response = await AF.download(buildRequest(url: url), to: destination)
            .validate()
            .downloadProgress { progress in
                callback?(progress)
            }
            .serializingDownloadedFileURL()
            .response
        if let error = response.error {
            throw unwrap(error)
        }
    }

    private func buildRequest(url: URL) -> URLRequest {
        var request = URLRequest(url: url)
        // Ignore the URL cache so that a cached response is never replayed for a deterministic request URL
        request.cachePolicy = .reloadIgnoringLocalCacheData
        request.httpMethod = HTTPMethod.get.rawValue
        request.timeoutInterval = Double(config.httpTimeout) / 1000.0
        return request
    }

    private func unwrap(_ error: AFError) -> Error {
        if let urlError = error.underlyingError as? URLError, urlError.code == .timedOut {
            return urlError
        }
        CAPLog.print("[", GraftPlugin.tag, "] ", "Request failed: \(error)")
        return CustomError.downloadFailed
    }
}
