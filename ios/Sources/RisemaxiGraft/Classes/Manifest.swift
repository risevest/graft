import Foundation

/// The signed description of a release. Its raw bytes are what the detached signature covers, so it
/// is decoded only after that signature has been verified.
public struct Manifest: Decodable {
    private static let supportedSchema = 1

    let id: String
    let channel: String
    let counter: Int
    let minNativeBuild: Int
    let notBefore: Int
    let expiresAt: Int
    let files: [ManifestFile]

    private enum CodingKeys: String, CodingKey {
        case schema, id, channel, counter, minNativeBuild, notBefore, expiresAt, files
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let schema = try container.decode(Int.self, forKey: .schema)
        guard schema == Manifest.supportedSchema else {
            throw DecodingError.dataCorruptedError(forKey: .schema, in: container, debugDescription: "Unsupported manifest schema: \(schema)")
        }
        id = try container.decode(String.self, forKey: .id)
        channel = try container.decode(String.self, forKey: .channel)
        counter = try container.decode(Int.self, forKey: .counter)
        minNativeBuild = try container.decode(Int.self, forKey: .minNativeBuild)
        notBefore = try container.decode(Int.self, forKey: .notBefore)
        expiresAt = try container.decode(Int.self, forKey: .expiresAt)
        files = try container.decode([ManifestFile].self, forKey: .files)
        guard !files.isEmpty else {
            throw DecodingError.dataCorruptedError(forKey: .files, in: container, debugDescription: "Manifest lists no files.")
        }
    }

    /// The path each digest can be read from in a bundle described by this manifest.
    var hrefBySha256: [String: String] {
        return Dictionary(files.map { ($0.sha256, $0.href) }, uniquingKeysWith: { first, _ in first })
    }
}
