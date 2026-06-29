plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}
repositories {
    mavenCentral()
    mavenLocal()
}
dependencies {
    implementation(libs.bundles.common)
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.client)
    // there was no slf4j backend at all before this, so any startup/connection errors just
    // vanished and the app looked like it was hanging instead of throwing
    runtimeOnly("ch.qos.logback:logback-classic:1.5.18")
    testImplementation(libs.bundles.ktor.test)
    testImplementation(kotlin("test"))
}
java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
application { mainClass = "io.nexure.discount.ApplicationKt" }

tasks {
    test {
        useJUnitPlatform()
        testLogging { events("passed", "skipped", "failed") }
    }
}
