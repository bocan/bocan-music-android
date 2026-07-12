// Applied only by the release workflow (never by everyday builds) to upload the signed
// AAB to the Play internal track. Keeping the Gradle Play Publisher plugin out of the
// normal build keeps day-to-day builds free of the dependency and keeps the release
// artifact reproducible for F-Droid, which forbids non-free build tooling in the graph.
//
// The plugin reads the service account JSON from the ANDROID_PUBLISHER_CREDENTIALS
// environment variable natively, so no credential path is configured here. The release
// workflow only invokes this script when that secret is present.
//
// Status: prepared but unvalidated. The Play Console corporate account is not yet
// approved, so this has never run against a real track. Validate the track name and the
// first upload manually before trusting the automation. See docs/release-checklist.md.

initscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.github.triplet.gradle:play-publisher:3.12.1")
    }
}

gradle.beforeProject {
    if (name == "app") {
        apply(plugin = "com.github.triplet.play")
        extensions.configure<com.github.triplet.gradle.play.PlayPublisherExtension>("play") {
            // Uploads land on the internal track for review; promote to production by hand
            // from the Play Console until a wider rollout is deliberately chosen.
            track.set("internal")
            defaultToAppBundles.set(true)
        }
    }
}
