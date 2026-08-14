import Foundation
import CryptoKit
import ZIPFoundation
import Capacitor

@objc public class Graft: NSObject {
    private let autoUpdateIntervalMs: Int64 = 15 * 60 * 1000 // 15 minutes
    private let cachesDirectoryUrl = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
    private let config: GraftConfig
    private let httpClient: GraftHttpClient
    private let plugin: GraftPlugin
    private let preferences: GraftPreferences

    private var rollbackDispatchWorkItem: DispatchWorkItem?
    private var rollbackPerformed = false
    private var lastAutoUpdateCheckTimestamp: Int64 = 0
    private var syncInProgress = false

    init(config: GraftConfig, plugin: GraftPlugin) {
        self.config = config
        self.httpClient = GraftHttpClient(config: config)
        self.plugin = plugin
        self.preferences = GraftPreferences()
        super.init()

        // Start the rollback timer to rollback to the last known good bundle
        // if the app is not ready after a certain time
        startRollbackTimer()
    }

    @objc public func clearBlockedBundles() {
        preferences.setBlockedBundleIds(nil)
    }

    @objc public func deleteBundle(_ options: DeleteBundleOptions, completion: @escaping (Error?) -> Void) {
        let bundleId = options.getBundleId()

        if !hasBundleById(bundleId) {
            completion(CustomError.bundleNotFound)
            return
        }

        do {
            try deleteBundleById(bundleId)
            completion(nil)
        } catch {
            completion(error)
        }
    }

    @objc public func downloadBundle(_ options: DownloadBundleOptions) async throws {
        let bundleId = options.getBundleId()

        if hasBundleById(bundleId) {
            throw CustomError.bundleAlreadyExists
        }
        guard let url = URL(string: options.getUrl()) else {
            throw CustomError.urlMissing
        }

        let zipFile = cachesDirectoryUrl.appendingPathComponent(UUID().uuidString + ".zip")
        defer { try? FileManager.default.removeItem(at: zipFile) }

        try await httpClient.download(url: url, to: zipFile, callback: { progress in
            self.notifyDownloadBundleProgressListeners(
                bundleId: bundleId,
                downloadedBytes: progress.completedUnitCount,
                totalBytes: progress.totalUnitCount
            )
        })
        try verifyChecksum(url: zipFile, expected: options.getChecksum())

        let directory = try unzipFile(zipFile: zipFile)
        guard let indexHtmlFile = searchIndexHtmlFile(url: directory) else {
            throw CustomError.bundleIndexHtmlMissing
        }
        try moveBundleIntoPlace(from: indexHtmlFile.deletingLastPathComponent(), bundleId: bundleId)
    }

    @objc public func getBlockedBundles(completion: @escaping (Result?, Error?) -> Void) {
        completion(GetBlockedBundlesResult(bundleIds: Array(getBlockedBundleIds())), nil)
    }

    @objc public func getChannel(completion: @escaping (Result?, Error?) -> Void) {
        completion(GetChannelResult(channel: getChannel()), nil)
    }

    @objc public func getCurrentBundle(completion: @escaping (Result?, Error?) -> Void) {
        completion(GetCurrentBundleResult(bundleId: getCurrentBundleId()), nil)
    }

    @objc public func getDownloadedBundles(completion: @escaping (Result?, Error?) -> Void) {
        completion(GetDownloadedBundlesResult(bundleIds: getDownloadedBundleIds()), nil)
    }

    @objc public func getInstallId(completion: @escaping (Result?, Error?) -> Void) {
        let installId = preferences.getInstallId()
        completion(GetInstallIdResult(installId: installId, bucket: ReleaseSelector.bucket(for: installId)), nil)
    }

    @objc public func getNextBundle(completion: @escaping (Result?, Error?) -> Void) {
        completion(GetNextBundleResult(bundleId: getNextBundleId()), nil)
    }

    @objc public func getVersionCode(completion: @escaping (Result?, Error?) -> Void) {
        completion(GetVersionCodeResult(versionCode: getVersionCode()), nil)
    }

    @objc public func getVersionName(completion: @escaping (Result?, Error?) -> Void) {
        completion(GetVersionNameResult(versionName: getVersionName()), nil)
    }

    @objc public func isSyncing(completion: @escaping (Result?, Error?) -> Void) {
        completion(IsSyncingResult(syncing: syncInProgress), nil)
    }

    @objc public func handleLoad() {
        if config.autoUpdateStrategy == "background" {
            performAutoUpdate()
        }
    }

    @objc public func handleAppWillEnterForeground() {
        if config.autoUpdateStrategy == "background" {
            performAutoUpdate()
        }
    }

    @objc public func ready(completion: @escaping (Result?, Error?) -> Void) {
        CAPLog.print("[", GraftPlugin.tag, "] ", "App is ready.")
        if config.readyTimeout <= 0 {
            CAPLog.print("[", GraftPlugin.tag, "] ", "Ready timeout is set to 0. Automatic rollback is disabled.")
        }
        // Stop the rollback timer
        stopRollbackTimer()
        // Delete unused bundles
        if config.autoDeleteBundles {
            deleteUnusedBundles()
        }
        // Get the current and previous bundle IDs
        let currentBundleId = getCurrentBundleId()
        let previousBundleId = preferences.getPreviousBundleId()
        // Block the rolled back bundle if enabled
        if config.autoBlockRolledBackBundles, rollbackPerformed, let previousBundleId = previousBundleId {
            addBlockedBundleId(previousBundleId)
        }
        // Return the result
        completion(ReadyResult(currentBundleId: currentBundleId, previousBundleId: previousBundleId, rollback: rollbackPerformed), nil)
        // Set the new previous bundle ID
        preferences.setPreviousBundleId(currentBundleId)
        // A bundle that reaches this point booted, so it is the one to roll back to next time
        if !rollbackPerformed {
            preferences.setLastKnownGoodBundleId(currentBundleId)
        }
        // Reset the rollback flag
        rollbackPerformed = false
    }

    @objc public func reload() {
        setCurrentBundleById(getNextBundleId())
        startRollbackTimer()
    }

    @objc public func reset() {
        setNextBundleById(nil)
    }

    @objc public func setChannel(_ options: SetChannelOptions, completion: @escaping (Error?) -> Void) {
        preferences.setChannel(options.getChannel())
        completion(nil)
    }

    @objc public func setNextBundle(_ options: SetNextBundleOptions, completion: @escaping (Error?) -> Void) {
        guard let bundleId = options.getBundleId() else {
            reset()
            completion(nil)
            return
        }
        if !hasBundleById(bundleId) {
            completion(CustomError.bundleNotFound)
            return
        }
        setNextBundleById(bundleId)
        completion(nil)
    }

    @objc public func sync(_ options: SyncOptions) async throws -> SyncResult {
        if syncInProgress {
            throw CustomError.syncInProgress
        }
        syncInProgress = true
        defer { syncInProgress = false }

        guard let channel = options.getChannel() ?? getChannel() else {
            throw CustomError.channelMissing
        }
        let publicKey = try loadPublicKey()
        let channelUrl = try buildChannelUrl(channel: channel)
        CAPLog.print("[", GraftPlugin.tag, "] Reading channel document: ", channelUrl)

        let document = try JSONDecoder().decode(ChannelDocument.self, from: try await httpClient.data(url: channelUrl))
        if document.killSwitch {
            CAPLog.print("[", GraftPlugin.tag, "] ", "Kill switch is enabled. Reverting to the embedded bundle.")
            setNextBundleById(nil)
            return SyncResult(nextBundleId: nil)
        }
        guard let release = ReleaseSelector.select(
            from: document.releases,
            versionCode: try nativeBuild(),
            highestInstalledCounter: preferences.getHighestInstalledCounter(),
            bucket: ReleaseSelector.bucket(for: preferences.getInstallId()),
            blockedBundleIds: getBlockedBundleIds()
        ) else {
            CAPLog.print("[", GraftPlugin.tag, "] ", "No update available.")
            return SyncResult(nextBundleId: nil)
        }
        if hasBundleById(release.id) {
            stageRelease(bundleId: release.id, counter: release.counter)
            return SyncResult(nextBundleId: release.id)
        }

        let manifestUrl = try resolveManifestUrl(channelUrl: channelUrl, manifest: release.manifest)
        CAPLog.print("[", GraftPlugin.tag, "] Reading manifest: ", manifestUrl)
        let manifestData = try await httpClient.data(url: manifestUrl)
        // The signature covers these exact bytes, so nothing is decoded before it is verified
        try verifySignature(content: manifestData, signature: release.sig, publicKey: publicKey)
        let manifest = try JSONDecoder().decode(Manifest.self, from: manifestData)
        try verifyManifestIsAcceptable(manifest, release: release, channel: channel)

        try await install(manifest: manifest, manifestData: manifestData, manifestUrl: manifestUrl)
        stageRelease(bundleId: manifest.id, counter: manifest.counter)
        return SyncResult(nextBundleId: manifest.id)
    }

    private func install(manifest: Manifest, manifestData: Data, manifestUrl: URL) async throws {
        let directory = cachesDirectoryUrl.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        do {
            let currentHrefBySha256 = loadCurrentHrefBySha256()
            var filesToDownload = [ManifestFile]()
            for file in manifest.files {
                guard let currentHref = currentHrefBySha256[file.sha256],
                      copyCurrentBundleFile(currentHref: currentHref, file: file, to: directory) else {
                    filesToDownload.append(file)
                    continue
                }
            }
            try await downloadBundleFiles(manifestUrl: manifestUrl, files: filesToDownload, to: directory, bundleId: manifest.id)

            guard FileManager.default.fileExists(atPath: directory.appendingPathComponent(GraftPointer.indexFileName).path) else {
                throw CustomError.bundleIndexHtmlMissing
            }
            // Written last so the verified file set is exactly what the manifest describes
            try manifestData.write(to: directory.appendingPathComponent(GraftPointer.manifestFileName))
            try moveBundleIntoPlace(from: directory, bundleId: manifest.id)
        } catch {
            try? FileManager.default.removeItem(at: directory)
            throw error
        }
    }

    /// Records the release as installed before it is staged, so a bundle that never boots still raises
    /// the downgrade floor and the device can only ever move forward.
    private func stageRelease(bundleId: String, counter: Int) {
        if counter > preferences.getHighestInstalledCounter() {
            preferences.setHighestInstalledCounter(counter)
        }
        if bundleId != getCurrentBundleId() {
            setNextBundleById(bundleId)
        }
    }

    private func verifyManifestIsAcceptable(_ manifest: Manifest, release: ChannelRelease, channel: String) throws {
        guard manifest.id == release.id,
              manifest.counter == release.counter,
              manifest.minNativeBuild == release.minNativeBuild,
              manifest.channel == channel,
              manifest.minNativeBuild <= (try nativeBuild()),
              manifest.counter > preferences.getHighestInstalledCounter() else {
            throw CustomError.manifestMismatch
        }
        let now = Int(Date().timeIntervalSince1970)
        guard now >= manifest.notBefore, now < manifest.expiresAt else {
            throw CustomError.manifestExpired
        }
    }

    private func buildChannelUrl(channel: String) throws -> URL {
        return try parseServerUrl()
            .appendingPathComponent("v1")
            .appendingPathComponent("channel")
            .appendingPathComponent("\(channel).json")
    }

    /// Confines the manifest to the configured origin. The signature already decides what may be
    /// installed; this keeps an edited channel document from pointing the device at another host.
    private func resolveManifestUrl(channelUrl: URL, manifest: String) throws -> URL {
        let serverUrl = try parseServerUrl()
        guard let manifestUrl = URL(string: manifest, relativeTo: channelUrl)?.absoluteURL,
              manifestUrl.scheme == serverUrl.scheme,
              manifestUrl.host == serverUrl.host,
              manifestUrl.port == serverUrl.port else {
            throw CustomError.manifestUrlInvalid
        }
        return manifestUrl
    }

    private func parseServerUrl() throws -> URL {
        guard let serverUrl = config.serverUrl else {
            throw CustomError.serverUrlMissing
        }
        guard let url = URL(string: serverUrl), url.scheme != nil, url.host != nil else {
            throw CustomError.serverUrlInvalid
        }
        return url
    }

    private func buildFileUrl(manifestUrl: URL, href: String) -> URL {
        var url = manifestUrl.deletingLastPathComponent()
        for segment in href.split(separator: "/") {
            url.appendPathComponent(String(segment))
        }
        return url
    }

    private func downloadBundleFiles(manifestUrl: URL, files: [ManifestFile], to directory: URL, bundleId: String) async throws {
        if files.isEmpty {
            return
        }
        let totalBytes = Int64(files.map { $0.size }.reduce(0, +))
        actor CompletedBytes {
            var value: Int64 = 0
            func add(_ amount: Int64) -> Int64 {
                value += amount
                return value
            }
        }
        let completedBytes = CompletedBytes()

        try await withThrowingTaskGroup(of: Void.self) { group in
            for file in files {
                group.addTask {
                    let destination = directory.appendingPathComponent(file.href)
                    try await self.httpClient.download(
                        url: self.buildFileUrl(manifestUrl: manifestUrl, href: file.href),
                        to: destination,
                        callback: { progress in
                            Task {
                                let downloaded = await completedBytes.value + progress.completedUnitCount
                                self.notifyDownloadBundleProgressListeners(
                                    bundleId: bundleId,
                                    downloadedBytes: downloaded,
                                    totalBytes: totalBytes
                                )
                            }
                        }
                    )
                    try self.verifyChecksum(url: destination, expected: file.sha256)
                    let downloaded = await completedBytes.add(Int64(file.size))
                    self.notifyDownloadBundleProgressListeners(bundleId: bundleId, downloadedBytes: downloaded, totalBytes: totalBytes)
                }
            }
            try await group.waitForAll()
        }
    }

    /// - Returns: Where each digest of the running bundle can be read from, so unchanged files are
    ///   copied rather than downloaded.
    private func loadCurrentHrefBySha256() -> [String: String] {
        let manifest: Manifest?
        if let currentBundleId = getCurrentBundleId() {
            manifest = GraftPointer.readManifest(
                at: GraftPointer.buildBundleDirectory(bundleId: currentBundleId).appendingPathComponent(GraftPointer.manifestFileName)
            )
        } else {
            manifest = GraftPointer.readEmbeddedManifest()
        }
        return manifest?.hrefBySha256 ?? [:]
    }

    private func copyCurrentBundleFile(currentHref: String, file: ManifestFile, to directory: URL) -> Bool {
        let source: URL
        if let currentBundleId = getCurrentBundleId() {
            source = GraftPointer.buildBundleDirectory(bundleId: currentBundleId).appendingPathComponent(currentHref)
        } else {
            source = GraftPointer.buildEmbeddedBundleDirectory().appendingPathComponent(currentHref)
        }
        let destination = directory.appendingPathComponent(file.href)
        do {
            try FileManager.default.createDirectory(at: destination.deletingLastPathComponent(), withIntermediateDirectories: true)
            try FileManager.default.copyItem(at: source, to: destination)
            try verifyChecksum(url: destination, expected: file.sha256)
            return true
        } catch {
            CAPLog.print("[", GraftPlugin.tag, "] ", "Failed to reuse file \(currentHref): \(error.localizedDescription)")
            try? FileManager.default.removeItem(at: destination)
            return false
        }
    }

    private func moveBundleIntoPlace(from source: URL, bundleId: String) throws {
        var bundlesDirectory = GraftPointer.buildBundlesDirectory()
        if !FileManager.default.fileExists(atPath: bundlesDirectory.path) {
            try FileManager.default.createDirectory(at: bundlesDirectory, withIntermediateDirectories: true)
            var resourceValues = URLResourceValues()
            resourceValues.isExcludedFromBackup = true
            try bundlesDirectory.setResourceValues(resourceValues)
        }
        try FileManager.default.moveItem(at: source, to: GraftPointer.buildBundleDirectory(bundleId: bundleId))
    }

    private func deleteBundleById(_ bundleId: String) throws {
        try FileManager.default.removeItem(at: GraftPointer.buildBundleDirectory(bundleId: bundleId))
        if bundleId == getNextBundleId() {
            setNextBundleById(nil)
        }
    }

    private func deleteUnusedBundles() {
        let currentBundleId = getCurrentBundleId()
        let nextBundleId = getNextBundleId()
        let lastKnownGoodBundleId = preferences.getLastKnownGoodBundleId()

        for bundleId in getDownloadedBundleIds() where
            bundleId != currentBundleId && bundleId != nextBundleId && bundleId != lastKnownGoodBundleId {
            do {
                try deleteBundleById(bundleId)
            } catch {
                CAPLog.print("[", GraftPlugin.tag, "] ", "Failed to delete bundle with id: \(bundleId)")
            }
        }
    }

    private func getDownloadedBundleIds() -> [String] {
        let url = GraftPointer.buildBundlesDirectory()
        guard FileManager.default.fileExists(atPath: url.path) else {
            return []
        }
        return (try? FileManager.default.contentsOfDirectory(atPath: url.path)) ?? []
    }

    private func getChannel() -> String? {
        var channel = config.defaultChannel
        if let nativeChannel = getNativeChannel() {
            channel = nativeChannel
        }
        if let storedChannel = preferences.getChannel() {
            channel = storedChannel
        }
        return channel
    }

    private func getNativeChannel() -> String? {
        return Bundle.main.object(forInfoDictionaryKey: "RisemaxiGraftDefaultChannel") as? String
    }

    /// - Returns: The current bundle ID or `nil` if the bundle embedded in the binary is in use.
    private func getCurrentBundleId() -> String? {
        guard let viewController = plugin.bridge?.viewController as? CAPBridgeViewController else {
            return nil
        }
        let bundleId = URL(fileURLWithPath: viewController.getServerBasePath()).lastPathComponent
        return bundleId == GraftPointer.embeddedWebAssetDir ? nil : bundleId
    }

    /// - Returns: The next bundle ID or `nil` if the bundle embedded in the binary will be used.
    private func getNextBundleId() -> String? {
        return GraftPointer.getActiveBundleId()
    }

    private func getVersionCode() -> String {
        return Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? ""
    }

    private func getVersionName() -> String {
        return Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
    }

    private func nativeBuild() throws -> Int {
        guard let nativeBuild = GraftPointer.readNativeBuild() else {
            throw CustomError.manifestMismatch
        }
        return nativeBuild
    }

    private func hasBundleById(_ bundleId: String) -> Bool {
        return FileManager.default.fileExists(atPath: GraftPointer.buildBundleDirectory(bundleId: bundleId).path)
    }

    private func notifyDownloadBundleProgressListeners(bundleId: String, downloadedBytes: Int64, totalBytes: Int64) {
        plugin.notifyDownloadBundleProgressListeners(
            DownloadBundleProgressEvent(bundleId: bundleId, downloadedBytes: downloadedBytes, totalBytes: totalBytes)
        )
    }

    private func performAutoUpdate() {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        if lastAutoUpdateCheckTimestamp > 0 && (now - lastAutoUpdateCheckTimestamp) < autoUpdateIntervalMs {
            CAPLog.print("[", GraftPlugin.tag, "] ", "Auto-update skipped. Last check was less than 15 minutes ago.")
            return
        }
        lastAutoUpdateCheckTimestamp = now

        Task {
            do {
                CAPLog.print("[", GraftPlugin.tag, "] ", "Auto-update started.")
                _ = try await sync(SyncOptions(channel: nil))
                CAPLog.print("[", GraftPlugin.tag, "] ", "Auto-update completed successfully.")
            } catch {
                CAPLog.print("[", GraftPlugin.tag, "] ", "Auto-update failed: ", error.localizedDescription)
            }
        }
    }

    private func rollback() {
        rollbackPerformed = true
        let currentBundleId = getCurrentBundleId()
        preferences.setPreviousBundleId(currentBundleId)
        guard currentBundleId != nil else {
            CAPLog.print("[", GraftPlugin.tag, "] ", "App is not ready. Embedded bundle is already in use.")
            return
        }
        let targetBundleId = resolveRollbackTargetBundleId()
        let target = targetBundleId == nil ? "the embedded bundle." : "bundle \(targetBundleId!)."
        CAPLog.print("[", GraftPlugin.tag, "] ", "App is not ready. Rolling back to \(target)")
        setNextBundleById(targetBundleId)
        setCurrentBundleById(targetBundleId)
    }

    private func resolveRollbackTargetBundleId() -> String? {
        guard let bundleId = preferences.getLastKnownGoodBundleId() else {
            return nil
        }
        if getBlockedBundleIds().contains(bundleId) || !hasBundleById(bundleId) {
            return nil
        }
        return bundleId
    }

    private func searchIndexHtmlFile(url: URL) -> URL? {
        guard let contents = try? FileManager.default.contentsOfDirectory(at: url, includingPropertiesForKeys: [.isDirectoryKey]) else {
            return nil
        }
        if let indexHtmlFile = contents.first(where: { $0.lastPathComponent == GraftPointer.indexFileName }) {
            return indexHtmlFile
        }
        for fileUrl in contents {
            var isDirectory: ObjCBool = false
            if FileManager.default.fileExists(atPath: fileUrl.path, isDirectory: &isDirectory), isDirectory.boolValue,
               let indexHtmlFile = searchIndexHtmlFile(url: fileUrl) {
                return indexHtmlFile
            }
        }
        return nil
    }

    /// - Parameter bundleId: The bundle ID to serve now. If `nil`, the bundle embedded in the binary is served.
    private func setCurrentBundleById(_ bundleId: String?) {
        guard let viewController = plugin.bridge?.viewController as? CAPBridgeViewController else {
            return
        }
        let path = bundleId == nil
            ? GraftPointer.buildEmbeddedBundleDirectory().path
            : GraftPointer.buildBundleDirectory(bundleId: bundleId!).path
        viewController.setServerBasePath(path: path)
        plugin.notifyReloadedListeners()
    }

    /// - Parameter bundleId: The bundle ID to serve on the next launch. If `nil`, the bundle embedded in the binary is served.
    private func setNextBundleById(_ bundleId: String?) {
        if let bundleId = bundleId {
            GraftPointer.setActiveBundleId(bundleId)
        } else {
            GraftPointer.clearActiveBundleId()
        }
        plugin.notifyNextBundleSetListeners(NextBundleSetEvent(bundleId: bundleId))
    }

    private func getBlockedBundleIds() -> Set<String> {
        guard let blockedIds = preferences.getBlockedBundleIds(), !blockedIds.isEmpty else {
            return []
        }
        return Set(blockedIds.split(separator: ",").map(String.init))
    }

    private func addBlockedBundleId(_ bundleId: String) {
        var blockedList = preferences.getBlockedBundleIds()?.split(separator: ",").map(String.init) ?? []
        if blockedList.contains(bundleId) {
            return
        }
        blockedList.append(bundleId)
        while blockedList.count > 100 {
            blockedList.removeFirst()
        }
        preferences.setBlockedBundleIds(blockedList.joined(separator: ","))
        CAPLog.print("[", GraftPlugin.tag, "] ", "Bundle blocked: ", bundleId)
    }

    private func startRollbackTimer() {
        guard config.readyTimeout > 0 else {
            return
        }
        stopRollbackTimer()
        let workItem = DispatchWorkItem { [weak self] in
            self?.rollback()
        }
        rollbackDispatchWorkItem = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + DispatchTimeInterval.milliseconds(config.readyTimeout), execute: workItem)
    }

    private func stopRollbackTimer() {
        rollbackDispatchWorkItem?.cancel()
    }

    private func unzipFile(zipFile: URL) throws -> URL {
        let destinationDirectory = cachesDirectoryUrl.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: destinationDirectory, withIntermediateDirectories: true)
        try FileManager.default.unzipItem(at: zipFile, to: destinationDirectory)
        return destinationDirectory
    }

    private func verifyChecksum(url: URL, expected: String) throws {
        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }
        var hasher = SHA256()
        while autoreleasepool(invoking: {
            let nextChunk = handle.readData(ofLength: 8192)
            guard !nextChunk.isEmpty else { return false }
            hasher.update(data: nextChunk)
            return true
        }) { }
        let checksum = hasher.finalize().map { String(format: "%02hhx", $0) }.joined()
        guard checksum == expected else {
            throw CustomError.checksumMismatch
        }
    }

    private func loadPublicKey() throws -> SecKey {
        guard let publicKey = config.publicKey else {
            throw CustomError.publicKeyMissing
        }
        let publicKeyAsBase64 = publicKey
            .replacingOccurrences(of: "-----BEGIN PUBLIC KEY-----", with: "")
            .replacingOccurrences(of: "-----END PUBLIC KEY-----", with: "")
            .replacingOccurrences(of: "\n", with: "")
        let attributes: [CFString: Any] = [
            kSecAttrKeyType: kSecAttrKeyTypeRSA,
            kSecAttrKeyClass: kSecAttrKeyClassPublic
        ]
        var error: Unmanaged<CFError>?
        guard let publicKeyData = Data(base64Encoded: publicKeyAsBase64),
              let key = SecKeyCreateWithData(publicKeyData as CFData, attributes as CFDictionary, &error) else {
            if let error = error?.takeRetainedValue() {
                CAPLog.print("[", GraftPlugin.tag, "] ", "Failed to create public key with error: \(error)")
            }
            throw CustomError.publicKeyInvalid
        }
        return key
    }

    private func verifySignature(content: Data, signature: String, publicKey: SecKey) throws {
        guard let signatureData = Data(base64Encoded: signature) else {
            throw CustomError.signatureVerificationFailed
        }
        var error: Unmanaged<CFError>?
        let verified = SecKeyVerifySignature(
            publicKey,
            .rsaSignatureMessagePKCS1v15SHA256,
            content as CFData,
            signatureData as CFData,
            &error
        )
        if let error = error?.takeRetainedValue() {
            CAPLog.print("[", GraftPlugin.tag, "] ", "Failed to verify signature with error: \(error)")
        }
        guard verified else {
            throw CustomError.signatureVerificationFailed
        }
    }
}
