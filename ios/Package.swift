// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "Kazium",
    platforms: [
        .iOS(.v16),
        .macOS(.v13)
    ],
    products: [
        .library(
            name: "KaziumKit",
            targets: ["KaziumKit"]
        ),
    ],
    dependencies: [],
    targets: [
        .target(
            name: "KaziumKit",
            dependencies: [],
            path: "Kazium"
        ),
    ]
)
