import CryptoKit
import Foundation

let fixtures = URL(fileURLWithPath: "\(ProcessInfo.processInfo.environment["FIXTURES"] ?? ".")")
let base = fixtures.appendingPathComponent("pt-a")
let target = fixtures.appendingPathComponent("pt-b")
let manifestData = try Data(contentsOf: target.appendingPathComponent("graft-manifest.json"))
let manifest = try JSONDecoder().decode(Manifest.self, from: manifestData)

func digest(_ url: URL) -> String? {
    guard let data = try? Data(contentsOf: url) else { return nil }
    return SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
}

func filesUnder(_ root: URL) -> [String] {
    guard let e = FileManager.default.enumerator(at: root, includingPropertiesForKeys: nil) else { return [] }
    return e.compactMap { ($0 as? URL) }
        .filter { (try? $0.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true }
        .map { $0.path.replacingOccurrences(of: root.path + "/", with: "") }
}

var failures = 0

func run(_ name: String, _ archive: String, expectSuccess: Bool) {
    let out = fixtures.appendingPathComponent("out-\(name)")
    try? FileManager.default.removeItem(at: out)
    try? FileManager.default.createDirectory(at: out, withIntermediateDirectories: true)

    var thrown: Error?
    do {
        try GraftPatch.apply(
            archive: fixtures.appendingPathComponent(archive),
            base: base,
            manifest: manifest,
            to: out
        )
    } catch {
        thrown = error
    }

    let produced = filesUnder(out)
    let escaped = FileManager.default.fileExists(atPath: fixtures.appendingPathComponent("passwd").path)

    if expectSuccess {
        let verified = manifest.files.allSatisfy { digest(out.appendingPathComponent($0.href)) == $0.sha256 }
        let ok = thrown == nil && verified && produced.count == manifest.files.count
        print("\(ok ? "PASS" : "FAIL")  \(name.padding(toLength: 16, withPad: " ", startingAt: 0)) applied \(produced.count)/\(manifest.files.count) files, all digests match: \(verified)")
        if !ok { failures += 1 }
    } else {
        // A rejection must (a) throw and (b) never leave a file whose digest is not the manifest's.
        let unverified = manifest.files.filter { file in
            guard let d = digest(out.appendingPathComponent(file.href)) else { return false }
            return d != file.sha256
        }
        let ok = thrown != nil && unverified.isEmpty && !escaped
        let reason = thrown.map { "\($0)" } ?? "DID NOT THROW"
        print("\(ok ? "PASS" : "FAIL")  \(name.padding(toLength: 16, withPad: " ", startingAt: 0)) rejected: \(reason); wrote \(produced.count) partial file(s), none mismatched: \(unverified.isEmpty)")
        if !ok { failures += 1 }
    }
    try? FileManager.default.removeItem(at: out)
}

print("manifest: \(manifest.files.count) files, release \(manifest.id)\n")
run("happy", "patch.gpz", expectSuccess: true)
run("truncated", "bad-truncated.gpz", expectSuccess: false)
run("payload", "bad-payload.gpz", expectSuccess: false)
run("missing-op", "bad-missing-op.gpz", expectSuccess: false)
run("bad-base", "bad-base.gpz", expectSuccess: false)
run("traversal", "bad-traversal.gpz", expectSuccess: false)
run("wrong-content", "bad-swapped.gpz", expectSuccess: false)

print("\n\(failures == 0 ? "ALL CASES PASSED" : "\(failures) CASE(S) FAILED")")
exit(failures == 0 ? 0 : 1)
