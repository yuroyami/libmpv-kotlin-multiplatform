# Releasing

Every step is a command or a click that the owner does. Nothing publishes on its own.

Releases go to Maven Central under the group `io.github.yuroyami`. Versions 0.3.0 and older stay in the old repository at `https://yuroyami.github.io/maven`, and nothing new goes there.

## Before the first release

The "Publish release" workflow needs four repository secrets:

| Secret | Value |
|---|---|
| `MAVEN_CENTRAL_USERNAME` | The user name of a Central Portal user token |
| `MAVEN_CENTRAL_PASSWORD` | The password of the same token |
| `SIGNING_IN_MEMORY_KEY` | An ASCII-armored GPG private key |
| `SIGNING_IN_MEMORY_KEY_PASSWORD` | The passphrase of that key |

The `io.github.yuroyami` namespace must be verified on the Central Portal.

## The steps

1. **Pins.** If an upstream version changes, edit `buildscripts/include/depinfo.sh`. Run `buildscripts/download-deps.sh --refresh`, then `scripts/check-gate.sh tier2`. Tier 2 builds arm64 and runs the device tests.
2. **Version and notes.** Set `settings.publishing.version` in `library.module-template.yaml`. Run `./kotlin do updateBuildInfo`, so `BuildInfo` carries the new version. Set the version in the install lines of `README.md`, `docs/index.md` and `docs/getting-started.md`. Add a `## [x.y.z] - YYYY-MM-DD` section to `CHANGELOG.md` that names what changed and the versions inside. Commit with the subject `Release x.y.z`. Push.
3. **Natives.** Run the "Release natives" workflow with the tag `vx.y.z`. The workflow creates the tag at the current commit if the tag does not exist. It builds the four ABIs and attaches the zips, the checksums, `SOURCES.txt` and the mpv and FFmpeg source tarballs to the release. Check the release page: eight library assets and three source assets.
4. **Publish.** Run the "Publish release" workflow with `version` set to `x.y.z`. Leave `release` off. The workflow runs on a macOS runner. It refuses a version that does not match `library.module-template.yaml`, has no dated changelog section, or is already on Maven Central. It downloads the release's zips, verifies them, checks them with `checkAndroidNatives`, and refuses unsigned files. It also runs `checkDesktopJni` and `checkNativeInterop`, which fail until the static mpv trees exist (see `docs/building-natives.md`). Then it uploads one deployment to the Central Portal.
5. **Release on the portal.** Open the deployment on the Central Portal and check the file list. Each of the seven modules is there: `libmpv-kmp-native`, `libmpv-kmp-android-natives`, `libmpv-kmp`, `libmpv-kmp-view`, `libmpv-kmp-compose`, `libmpv-kmp-canvas` and `libmpv-kmp-canvas-android-natives`. Press Publish. If something is wrong, drop the deployment instead, fix the cause, and run step 4 again.
6. **Check.** For each module, `https://repo1.maven.org/maven2/io/github/yuroyami/<module>/x.y.z/` answers 200. The files can take some time to appear after the Publish click.
7. **Docs.** Run the "Docs" workflow to deploy the site for the new version.
8. **Consumers.** Only now bump the version in consuming projects. A consumer pinned to a version that Maven Central does not serve yet has red CI until it does.

Maven Central never replaces or deletes a published version. A wrong version is followed by a corrected one.

## Publishing from a machine

The workflow is the normal path. A machine that publishes must be an arm64 Mac with the static mpv trees in place, because the macOS JNI library and the Apple klibs build there. To publish from a machine instead, set the four values in these variables: `KOTLIN_TOOLCHAIN_MAVEN_CENTRAL_USERNAME`, `KOTLIN_TOOLCHAIN_MAVEN_CENTRAL_PASSWORD`, `KOTLIN_TOOLCHAIN_SIGNING_KEY` and `KOTLIN_TOOLCHAIN_SIGNING_KEY_PASSPHRASE`. Then run:

```bash
.github/scripts/fetch-natives.sh vx.y.z
./kotlin do checkAndroidNatives
./kotlin do checkDesktopJni
./kotlin do checkNativeInterop
./kotlin publish mavenCentral -m libmpv-kmp-native -m libmpv-kmp-android-natives -m libmpv-kmp \
    -m libmpv-kmp-view -m libmpv-kmp-compose -m libmpv-kmp-canvas -m libmpv-kmp-canvas-android-natives
```

Then do steps 5 to 8.

## Rebuilding an old version

The native zips of every version stay on its GitHub release page. To rebuild a version on any machine, check out its tag. Run `.github/scripts/fetch-natives.sh vx.y.z`, then `./kotlin publish mavenLocal` with the same `-m` flags. Tags before 0.4.0 build with Gradle: run `./gradlew publishToMavenLocal` there instead.
