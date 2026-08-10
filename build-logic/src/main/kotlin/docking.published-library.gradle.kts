plugins {
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    publishToMavenCentral()
    // Central rejects unsigned artifacts, so sign whenever signing credentials are
    // available: an in-memory key (the release workflow's secrets) or a local GPG
    // keyring configured in ~/.gradle/gradle.properties. Builds with neither - CI
    // checks, contributors - still assemble, they just skip signing.
    val signingConfigured = providers.gradleProperty("signingInMemoryKey").isPresent ||
        providers.gradleProperty("signing.keyId").isPresent
    if (signingConfigured) {
        signAllPublications()
    }
}
