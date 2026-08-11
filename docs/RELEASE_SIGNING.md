# Release signing

Release signing is intentionally opt-in. No keystore, password, or private key belongs in this repository.

For local signing, create an ignored `keystore.properties` at the repository root:

```properties
storeFile=keystore/wtybill-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

The same four values may be supplied through Gradle properties or injected by the CI runner from its secret store:

- `ANDROID_KEYSTORE_PATH` / `storeFile`
- `ANDROID_KEYSTORE_PASSWORD` / `storePassword`
- `ANDROID_KEY_ALIAS` / `keyAlias`
- `ANDROID_KEY_PASSWORD` / `keyPassword`

When all four values are present, `:app:assembleRelease` and `:app:bundleRelease` use the supplied keystore. If any value is absent, Gradle produces unsigned artifacts; Release never silently signs with the Debug key.

The CI job should verify the artifact with `apksigner verify`/`jarsigner`, archive the checksum, and publish only from a protected branch. The runtime dynamic-signature JavaScript remains the same in either build mode; signing configuration does not add product capabilities.

## GitHub Actions

The repository workflow at `.github/workflows/android-release.yml` runs on version tags matching `v*.*.*` and can also be started manually. It always requires a formal signing key for CI builds; missing secrets fail the job instead of producing an accidentally unsigned release.

Configure these repository or environment secrets in GitHub Actions:

- `WTYBILL_KEYSTORE_BASE64`: Base64-encoded `keystore/wtybill-release.jks`.
- `WTYBILL_KEYSTORE_PASSWORD`: Keystore password.
- `WTYBILL_KEY_ALIAS`: Release key alias.
- `WTYBILL_KEY_PASSWORD`: Release key password.

A version tag builds and verifies the signed APK/AAB, uploads the artifacts, and creates a GitHub Release with `SHA256SUMS`. Manual runs build and verify artifacts by default; enable `publish_release` and provide a semantic version tag only when publication is intended.

The formal JKS release keystore is kept outside version control, with its credentials in the ignored local `keystore.properties`. The signed release APK passed `apksigner verify`, and the AAB passed `jarsigner -verify` with its JAR signature entries present. Back up the keystore and its credentials securely; losing them would prevent updates to an already-published application.
