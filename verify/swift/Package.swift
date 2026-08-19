// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "patchtest",
    platforms: [.macOS(.v13)],
    dependencies: [.package(url: "https://github.com/facebook/zstd.git", from: "1.5.6")],
    targets: [
        .executableTarget(
            name: "patchtest",
            dependencies: [.product(name: "libzstd", package: "zstd")])
    ]
)
