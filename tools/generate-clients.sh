#!/usr/bin/env bash
#
# Regenerates the mobile API clients from the committed OpenAPI spec.
#
# The clients are committed rather than generated during the app builds. An
# Xcode build cannot easily run a Java code generator, and making either app's
# build depend on one would mean a contributor needs a JDK to build the iOS
# app. Committing the output also makes an API change visible as a diff in the
# client, which is the point.
#
# Regenerate the spec first if the backend changed:
#   cd backend && ./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.write=true
#
set -euo pipefail

VERSION="7.25.0"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SPEC="$ROOT/backend/src/main/resources/openapi/openapi.json"
CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/watch-guru"
JAR="$CACHE/openapi-generator-cli-$VERSION.jar"

[ -f "$SPEC" ] || { echo "Spec not found: $SPEC" >&2; exit 1; }

if [ ! -f "$JAR" ]; then
    mkdir -p "$CACHE"
    echo "Fetching openapi-generator $VERSION..."
    curl -fsSL -o "$JAR" \
        "https://repo.maven.apache.org/maven2/org/openapitools/openapi-generator-cli/$VERSION/openapi-generator-cli-$VERSION.jar"
fi

generate() {
    echo "Generating $1 client..."
    shift
    java -jar "$JAR" generate "$@" >/dev/null
}

# Android: Retrofit + coroutines + kotlinx.serialization, which is what the app
# module is wired for.
rm -rf "$ROOT/android/api-client/src"
generate Kotlin \
    -i "$SPEC" -g kotlin --library jvm-retrofit2 \
    -o "$ROOT/android/api-client" \
    --additional-properties=packageName=dev.dhuelin.watchguru.api,serializationLibrary=kotlinx_serialization,useCoroutines=true,dateLibrary=java8

# iOS: swift6 gives async/await and Sendable models, matching the SwiftUI app's
# concurrency model. swift-combine would mean bridging publishers back to async.
rm -rf "$ROOT/ios/WatchGuruAPI/Sources" "$ROOT/ios/WatchGuruAPI/Package.swift"
generate Swift \
    -i "$SPEC" -g swift6 \
    -o "$ROOT/ios/WatchGuruAPI" \
    --additional-properties=projectName=WatchGuruAPI,responseAs=AsyncAwait,useSPMFileStructure=true,swiftPackagePath=.

# Prune scaffolding neither app uses: CocoaPods/Carthage manifests, an
# XcodeGen project, generated placeholder tests that assert nothing, and a
# Gradle wrapper for a module that is built by the app's own wrapper.
rm -rf "$ROOT/ios/WatchGuruAPI"/{Cartfile,WatchGuruAPI.podspec,git_push.sh,project.yml,docs}
rm -rf "$ROOT/ios/WatchGuruAPI"/{.openapi-generator-ignore,.gitignore,.swiftformat}
rm -rf "$ROOT/android/api-client"/{gradlew,gradlew.bat,gradle,settings.gradle,build.gradle,docs}
rm -rf "$ROOT/android/api-client"/{.openapi-generator-ignore,.gitignore,proguard-rules.pro,README.md}
rm -rf "$ROOT/android/api-client/src/test"

echo
echo "Done. Review the diff before committing:"
echo "  git diff --stat android/api-client ios/WatchGuruAPI"
