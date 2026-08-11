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

The signing path was smoke-tested locally with a one-day temporary PKCS#12 keystore outside the repository: the compressed release APK passed `apksigner verify` (v2) and the AAB passed `jarsigner -verify`. That certificate is disposable and is not a release identity; production signing still requires the distribution keystore supplied by the release owner or CI secret store.
