import Foundation

public enum CustomError: Error {
    case bundleAlreadyExists
    case bundleIdMissing
    case bundleIndexHtmlMissing
    case bundleNotFound
    case channelMissing
    case checksumMismatch
    case checksumMissing
    case downloadFailed
    case httpTimeout
    case installFailed
    case manifestContractUnmet
    case manifestExpired
    case manifestMismatch
    case manifestNameCollision
    case manifestUrlInvalid
    case nativeBuildInvalid
    case notInitialized
    case patchChecksumMismatch
    case patchFailed
    case publicKeyInvalid
    case publicKeyMissing
    case serverUrlInvalid
    case serverUrlMissing
    case signatureVerificationFailed
    case syncInProgress
    case urlMissing
}

extension CustomError: LocalizedError {
    public var errorDescription: String? {
        switch self {
        case .bundleAlreadyExists:
            return NSLocalizedString("bundle already exists.", comment: "bundleAlreadyExists")
        case .bundleIdMissing:
            return NSLocalizedString("bundleId must be provided.", comment: "bundleIdMissing")
        case .bundleIndexHtmlMissing:
            return NSLocalizedString("The bundle does not contain an index.html file.", comment: "bundleIndexHtmlMissing")
        case .bundleNotFound:
            return NSLocalizedString("bundle not found.", comment: "bundleNotFound")
        case .channelMissing:
            return NSLocalizedString("channel must be configured.", comment: "channelMissing")
        case .checksumMismatch:
            return NSLocalizedString("Checksum mismatch.", comment: "checksumMismatch")
        case .checksumMissing:
            return NSLocalizedString("checksum must be provided.", comment: "checksumMissing")
        case .downloadFailed:
            return NSLocalizedString("Bundle could not be downloaded.", comment: "downloadFailed")
        case .httpTimeout:
            return NSLocalizedString("Request timed out.", comment: "httpTimeout")
        case .installFailed:
            return NSLocalizedString("Bundle could not be installed.", comment: "installFailed")
        case .manifestContractUnmet:
            return NSLocalizedString("The release needs a plugin this build does not have.", comment: "manifestContractUnmet")
        case .manifestExpired:
            return NSLocalizedString("The manifest is not valid at the current time.", comment: "manifestExpired")
        case .manifestMismatch:
            return NSLocalizedString("The manifest does not describe an acceptable release.", comment: "manifestMismatch")
        case .manifestNameCollision:
            return NSLocalizedString(
                "A file in the release has the same name as the manifest, so one overwrites the other. Serve the manifest as graft-manifest.json.",
                comment: "manifestNameCollision")
        case .manifestUrlInvalid:
            return NSLocalizedString("The manifest URL is not on the configured server.", comment: "manifestUrlInvalid")
        case .nativeBuildInvalid:
            return NSLocalizedString("CFBundleVersion must be an integer to compare against minNativeBuild.", comment: "nativeBuildInvalid")
        case .notInitialized:
            return NSLocalizedString("Graft failed to initialize.", comment: "notInitialized")
        case .patchChecksumMismatch:
            return NSLocalizedString("A patched file did not match the manifest.", comment: "patchChecksumMismatch")
        case .patchFailed:
            return NSLocalizedString("The patch could not be applied.", comment: "patchFailed")
        case .publicKeyInvalid:
            return NSLocalizedString("Invalid public key.", comment: "publicKeyInvalid")
        case .publicKeyMissing:
            return NSLocalizedString("publicKey must be configured.", comment: "publicKeyMissing")
        case .serverUrlInvalid:
            return NSLocalizedString("Invalid serverUrl.", comment: "serverUrlInvalid")
        case .serverUrlMissing:
            return NSLocalizedString("serverUrl must be configured.", comment: "serverUrlMissing")
        case .signatureVerificationFailed:
            return NSLocalizedString("Signature verification failed.", comment: "signatureVerificationFailed")
        case .syncInProgress:
            return NSLocalizedString("Sync is already in progress.", comment: "syncInProgress")
        case .urlMissing:
            return NSLocalizedString("url must be provided.", comment: "urlMissing")
        }
    }
}
