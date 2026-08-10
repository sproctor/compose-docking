# Releasing

Releases are cut by pushing a tag. `.github/workflows/release.yml` does the rest on a
macOS runner - the only one that can compile the iOS klibs, which a Central deployment
has to include alongside every other target.

1. Set `VERSION_NAME` in `gradle.properties` to the release version (no `-SNAPSHOT`).
2. Commit and push it to `main`.
3. Tag and push:

   ```sh
   git tag v0.1.0
   git push origin v0.1.0
   ```

4. Bump `VERSION_NAME` to the next `-SNAPSHOT` and push.

The workflow refuses to run if the tag and `VERSION_NAME` disagree, runs the JVM tests,
then uploads all modules as a single deployment and releases it. A deployment that fails
Central's validation is never published, so a failed run leaves nothing behind.

## Credentials

Repository secrets, all four required:

| Secret | Gradle property |
|---|---|
| `MAVEN_CENTRAL_USERNAME` | `mavenCentralUsername` |
| `MAVEN_CENTRAL_PASSWORD` | `mavenCentralPassword` |
| `SIGNING_IN_MEMORY_KEY` | `signingInMemoryKey` |
| `SIGNING_IN_MEMORY_KEY_PASSWORD` | `signingInMemoryKeyPassword` |

The username and password are a Central Portal user token, not portal login
credentials - generate one at https://central.sonatype.com/account.

`SIGNING_IN_MEMORY_KEY` is the ASCII-armored private key:

```sh
gpg --armor --export-secret-keys <key-id> | gh secret set SIGNING_IN_MEMORY_KEY
```

Publishing locally works too, without any of the above: `docking.published-library`
also signs with a local keyring, so a `~/.gradle/gradle.properties` carrying
`mavenCentralUsername`/`Password` and `signing.keyId`/`password`/`secretKeyRingFile` can
run `./gradlew publishAndReleaseToMavenCentral --no-configuration-cache`. Apple targets
still need macOS.
