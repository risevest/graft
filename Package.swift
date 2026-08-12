// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "RisemaxiGraft",
    platforms: [.iOS(.v15)],
    products: [
        .library(
            name: "RisemaxiGraft",
            targets: ["Graft"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "8.0.0"),
        .package(url: "https://github.com/Alamofire/Alamofire.git", .upToNextMajor(from: "5.10.2")),
        .package(url: "https://github.com/weichsel/ZIPFoundation.git", .upToNextMinor(from: "0.9.0"))
    ],
    targets: [
        .target(
            name: "Graft",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm"),
                .product(name: "Alamofire", package: "Alamofire"),
                .product(name: "ZIPFoundation", package: "ZIPFoundation")
            ],
            path: "ios/Sources/Graft")
    ]
)
