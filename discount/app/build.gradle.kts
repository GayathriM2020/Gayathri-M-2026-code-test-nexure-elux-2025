plugins {
    alias(libs.plugins.kotlin.jvm)
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
