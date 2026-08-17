import Foundation

/// The list of releases published to a channel. It is served unsigned and edge-cached, so it is only
/// ever used to choose a manifest to fetch.
public struct ChannelDocument: Decodable {
    private static let supportedSchema = 1

    let killSwitch: Bool
    let releases: [ChannelRelease]

    private enum CodingKeys: String, CodingKey {
        case schema, killSwitch, releases
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let schema = try container.decode(Int.self, forKey: .schema)
        guard schema == ChannelDocument.supportedSchema else {
            throw DecodingError.dataCorruptedError(forKey: .schema, in: container, debugDescription: "Unsupported channel document schema: \(schema)")
        }
        killSwitch = try container.decodeIfPresent(Bool.self, forKey: .killSwitch) ?? false
        releases = try container.decode([ChannelRelease].self, forKey: .releases)
    }
}
