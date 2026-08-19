import CryptoKit
import Foundation
import libzstd

/// The plan says how to reconstruct each file, never whether the result is acceptable: drive this from
/// the signed manifest's file list, not from the plan.
enum GraftPatch {
    private static let magic: [UInt8] = Array("GRAFTP1\n".utf8)
    private static let schema = 1
    private static let windowLogMax: Int32 = 27

    static func apply(archive: URL, base: URL, manifest: Manifest, to directory: URL) throws {
        let container = try decompress([UInt8](try Data(contentsOf: archive)), prefix: nil, expectedSize: nil)
        var offset = 0

        guard container.count >= magic.count, Array(container[0..<magic.count]) == magic else {
            throw CustomError.patchFailed
        }
        offset = magic.count

        let planBytes = try readBlock(container, &offset)
        guard
            let plan = try JSONSerialization.jsonObject(with: Data(planBytes)) as? [String: Any],
            plan["schema"] as? Int == schema,
            let operations = plan["ops"] as? [[String: Any]]
        else {
            throw CustomError.patchFailed
        }

        let payloadCount = try readLength(container, &offset)
        var payloads = [[UInt8]]()
        payloads.reserveCapacity(payloadCount)
        for _ in 0..<payloadCount {
            payloads.append(try readBlock(container, &offset))
        }
        guard offset == container.count else {
            throw CustomError.patchFailed
        }

        var operationByHref = [String: [String: Any]]()
        for operation in operations {
            guard let href = operation["href"] as? String, operation["op"] as? String != "delete" else {
                continue
            }
            operationByHref[href] = operation
        }

        for file in manifest.files {
            guard let operation = operationByHref[file.href], let kind = operation["op"] as? String else {
                throw CustomError.patchFailed
            }

            let content: [UInt8]
            switch kind {
            case "keep":
                content = try readBase(base, operation)
            case "patch":
                content = try decompress(
                    try payload(payloads, operation),
                    prefix: try readBase(base, operation),
                    expectedSize: file.size
                )
            case "add":
                content = try payload(payloads, operation)
            default:
                throw CustomError.patchFailed
            }

            guard SHA256.hash(data: Data(content)).map({ String(format: "%02x", $0) }).joined() == file.sha256 else {
                throw CustomError.patchChecksumMismatch
            }

            let destination = directory.appendingPathComponent(file.href)
            try FileManager.default.createDirectory(
                at: destination.deletingLastPathComponent(),
                withIntermediateDirectories: true
            )
            try Data(content).write(to: destination)
        }
    }

    private static func decompress(_ input: [UInt8], prefix: [UInt8]?, expectedSize: Int?) throws -> [UInt8] {
        guard let context = ZSTD_createDCtx() else {
            throw CustomError.patchFailed
        }
        defer { ZSTD_freeDCtx(context) }

        guard ZSTD_isError(ZSTD_DCtx_setParameter(context, ZSTD_d_windowLogMax, windowLogMax)) == 0 else {
            throw CustomError.patchFailed
        }

        let size: Int
        if let expectedSize {
            size = expectedSize
        } else {
            let declared = input.withUnsafeBufferPointer { ZSTD_getFrameContentSize($0.baseAddress, $0.count) }
            guard declared != UInt64.max, declared != UInt64.max - 1, declared <= UInt64(Int.max) else {
                throw CustomError.patchFailed
            }
            size = Int(declared)
        }
        guard size >= 0 else {
            throw CustomError.patchFailed
        }

        var output = [UInt8](repeating: 0, count: size)
        let written: Int = try output.withUnsafeMutableBufferPointer { out in
            try input.withUnsafeBufferPointer { source in
                if let prefix {
                    let referenced = prefix.withUnsafeBufferPointer {
                        ZSTD_DCtx_refPrefix(context, $0.baseAddress, $0.count)
                    }
                    guard ZSTD_isError(referenced) == 0 else {
                        throw CustomError.patchFailed
                    }
                }
                return ZSTD_decompressDCtx(context, out.baseAddress, out.count, source.baseAddress, source.count)
            }
        }

        guard ZSTD_isError(written) == 0, written == size else {
            throw CustomError.patchFailed
        }
        return output
    }

    private static func readBase(_ base: URL, _ operation: [String: Any]) throws -> [UInt8] {
        guard let data = try? Data(contentsOf: base.appendingPathComponent(try safeHref(operation, "from"))) else {
            throw CustomError.patchFailed
        }
        return [UInt8](data)
    }

    private static func payload(_ payloads: [[UInt8]], _ operation: [String: Any]) throws -> [UInt8] {
        guard let index = operation["payload"] as? Int, index >= 0, index < payloads.count else {
            throw CustomError.patchFailed
        }
        return payloads[index]
    }

    private static func readLength(_ container: [UInt8], _ offset: inout Int) throws -> Int {
        guard offset + 4 <= container.count else {
            throw CustomError.patchFailed
        }
        var value = 0
        for byte in container[offset..<(offset + 4)] {
            value = (value << 8) | Int(byte)
        }
        offset += 4
        guard value >= 0, offset + value <= container.count else {
            throw CustomError.patchFailed
        }
        return value
    }

    private static func readBlock(_ container: [UInt8], _ offset: inout Int) throws -> [UInt8] {
        let length = try readLength(container, &offset)
        let block = Array(container[offset..<(offset + length)])
        offset += length
        return block
    }

    /// The plan is untrusted input, so its hrefs get the same relative-path rule as the manifest's.
    private static func safeHref(_ operation: [String: Any], _ key: String) throws -> String {
        guard let href = operation[key] as? String, !href.isEmpty, !href.hasPrefix("/") else {
            throw CustomError.patchFailed
        }
        for segment in href.split(separator: "/", omittingEmptySubsequences: false) {
            if segment.isEmpty || segment == "." || segment == ".." {
                throw CustomError.patchFailed
            }
        }
        return href
    }
}
