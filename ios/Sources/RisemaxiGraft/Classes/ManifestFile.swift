import Foundation

public struct ManifestFile: Decodable {
    let href: String
    let sha256: String
    let size: Int

    private enum CodingKeys: String, CodingKey {
        case href, sha256, size
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        href = try container.decode(String.self, forKey: .href)
        guard ManifestFile.isRelativePath(href) else {
            throw DecodingError.dataCorruptedError(forKey: .href, in: container, debugDescription: "href must be a relative path: \(href)")
        }
        sha256 = try container.decode(String.self, forKey: .sha256)
        guard sha256.count == 64 else {
            throw DecodingError.dataCorruptedError(forKey: .sha256, in: container, debugDescription: "sha256 must be a hex-encoded SHA-256 digest: \(sha256)")
        }
        size = try container.decode(Int.self, forKey: .size)
        guard size >= 0 else {
            throw DecodingError.dataCorruptedError(forKey: .size, in: container, debugDescription: "size must not be negative: \(size)")
        }
    }

    private static func isRelativePath(_ href: String) -> Bool {
        if href.isEmpty || href.hasPrefix("/") || href.contains("\\") || href.contains("//") {
            return false
        }
        return !href.split(separator: "/", omittingEmptySubsequences: false).contains { $0 == "." || $0 == ".." }
    }
}
