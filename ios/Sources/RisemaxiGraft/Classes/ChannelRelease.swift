import Foundation

/// One entry of a channel document. Every value here is an unverified hint used to decide which
/// manifest to fetch; the manifest itself carries the signed copies that are enforced.
public struct ChannelRelease: Decodable {
    let id: String
    let counter: Int
    let rollout: Int
    let minNativeBuild: Int
    let manifest: String
    let sig: String

    private enum CodingKeys: String, CodingKey {
        case id, counter, rollout, minNativeBuild, manifest, sig
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        counter = try container.decode(Int.self, forKey: .counter)
        rollout = try container.decode(Int.self, forKey: .rollout)
        guard rollout >= 0 && rollout <= 100 else {
            throw DecodingError.dataCorruptedError(forKey: .rollout, in: container, debugDescription: "rollout must be between 0 and 100: \(rollout)")
        }
        minNativeBuild = try container.decode(Int.self, forKey: .minNativeBuild)
        manifest = try container.decode(String.self, forKey: .manifest)
        sig = try container.decode(String.self, forKey: .sig)
    }
}
