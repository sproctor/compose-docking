plugins {
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    publishToMavenCentral()
    // Sign only when release signing keys are configured (CI / local release setup).
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
}
