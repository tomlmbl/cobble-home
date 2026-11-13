# Reproducible builds

Release APKs of Cobble Home can be rebuilt from source and compared byte for byte, apart from the signature.

## Signing certificate

Every release is signed with the same key:

- Certificate: `CN=tomlmbl`
- SHA-256: `78480f7e2f23440c8e516976db67527d975370b3eec2b511822cdb59f06de6c6`

Check a downloaded APK with `apksigner verify --print-certs <apk>`.

## Versions

- Git tag `vX.Y.Z` or `vX.Y.Z-beta.N`, matching `versionName` in `app/build.gradle.kts`.
- `versionCode` increases with every published release and is never reused.

## Environment

- JDK 21 (Zulu 21.0.13 is used for releases). The Gradle daemon refuses any other major version
  (`gradle/gradle-daemon-jvm.properties`).
- Android SDK with `platforms;android-37.0` and `build-tools;36.0.0`.

The APK records the git commit it was built from (`META-INF/version-control-info.textproto`). Build from a git
checkout of the tag, not from a source archive.

## Rebuild and compare

```sh
git clone https://github.com/tomlmbl/cobble-home.git && cd cobble-home
git checkout vX.Y.Z
./gradlew clean :app:assembleRelease
sha256sum app/build/outputs/apk/release/app-release-unsigned.apk
```

On macOS, use `shasum -a 256` instead of `sha256sum`.

1. Pushing a version tag runs the "Reproducible build" workflow (`.github/workflows/reproducible-build.yml`), which
   builds the same unsigned APK on Linux and prints its SHA-256 in the job summary. Your hash should match it.
2. To compare your unsigned build with the published, signed APK, copy the signature over and compare, as F-Droid does:

   ```sh
   apksigcopier compare <published.apk> --unsigned app/build/outputs/apk/release/app-release-unsigned.apk
   ```

   `apksigcopier` is a Python package; it needs `apksigner` from the Android build tools on `PATH`.

If the hashes differ, `diffoscope` (or `unzip` followed by `cmp`) shows which files differ.
