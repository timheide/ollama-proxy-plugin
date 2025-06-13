plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "de.timheide"
version = "0.1.0"

repositories {
    mavenCentral()
}

intellij {
    version.set("2024.1.7")
    type.set("IC") // Target IDE Platform

    plugins.set(listOf(/* Plugin Dependencies */))
}

kotlin {
    jvmToolchain(17)
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    patchPluginXml {
        sinceBuild.set("241")
        untilBuild.set("251.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}

dependencies {
    implementation("io.ktor:ktor-client-core:2.3.13")
    implementation("io.ktor:ktor-client-cio:2.3.13")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.13")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.13")
    implementation("io.ktor:ktor-server-core:2.3.13")
    implementation("io.ktor:ktor-server-netty:2.3.13")
    implementation("io.ktor:ktor-server-call-logging:2.3.13")
    implementation("io.ktor:ktor-server-double-receive:2.3.13")
    implementation("io.ktor:ktor-server-double-receive:2.3.13")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.13")
    implementation("io.ktor:ktor-serialization-jackson:2.3.13")
    implementation("io.ktor:ktor-server-status-pages:2.3.13")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("org.slf4j:slf4j-api:2.0.3")
    implementation("ch.qos.logback:logback-classic:1.5.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Testing dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.2")
    testImplementation("io.mockk:mockk:1.13.5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.ktor:ktor-client-mock:2.3.13")
    testImplementation("io.ktor:ktor-server-test-host:2.3.13")
    testImplementation("org.assertj:assertj-core:3.24.2")
}
