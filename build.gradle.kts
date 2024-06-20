import com.diffplug.gradle.spotless.SpotlessExtension

val mvnGroupId = "io.github.wcarmon"
val mvnArtifactId = "cache-utils-jvm" // see settings.gradle.kts
val mvnVersion = "1.0.0"

val ossrhPassword: String = providers.gradleProperty("ossrhPassword").getOrElse("")
val ossrhUsername: String = providers.gradleProperty("ossrhUsername").getOrElse("")

repositories {
    mavenCentral()
}

plugins {
    java
    id("com.diffplug.spotless") version "6.23.3"

    `java-library`
    `maven-publish`
    signing
}

group = mvnGroupId
version = mvnVersion

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks {
    compileTestJava {
        sourceCompatibility = JavaVersion.VERSION_21.toString()
        targetCompatibility = JavaVersion.VERSION_21.toString()
    }
}

dependencies {

    // TODO: remove lombok
    annotationProcessor("org.projectlombok:lombok:1.18.30")
    compileOnly("org.projectlombok:lombok:1.18.30")

    implementation("org.jetbrains:annotations:24.1.0")

    //testImplementation("com.google.guava:guava:33.2.1-jre")
    //testImplementation("org.apache.commons:commons-lang3:3.14.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testImplementation("org.mockito:mockito-core:5.12.0")
}

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = mvnGroupId
            artifactId = mvnArtifactId
            version = mvnVersion

            from(components["java"])

            suppressPomMetadataWarningsFor("runtimeElements")

            versionMapping {

            }

            pom {
                name = mvnArtifactId
                description = "Utilities for using Property instances"
                url = "https://github.com/wcarmon/cache-utils-jvm"

                licenses {
                    license {
                        name = "MIT License"
                        url =
                            "https://raw.githubusercontent.com/wcarmon/cache-utils-jvm/main/LICENSE"
                    }
                }

                developers {
                    developer {
                        email = "github@wcarmon.com"
                        id = "wcarmon"
                        name = "Wil Carmon"
                        organization = ""
                    }
                }

                scm {
                    connection =
                        "scm:git:git@github.com:wcarmon/cache-utils-jvm.git"
                    developerConnection =
                        "scm:git:ssh://github.com:wcarmon/cache-utils-jvm.git"
                    url = "https://github.com/wcarmon/cache-utils-jvm/tree/main"
                }
            }
        }
    }

    repositories {
        maven {

            // -- See ~/.gradle/gradle.properties
            credentials {
                username = ossrhUsername
                password = ossrhPassword
            }

            val releasesRepoUrl =
                uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")

            val snapshotsRepoUrl = uri(layout.buildDirectory.dir("repos/snapshots")) // TODO: fix

            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl
            else releasesRepoUrl // TODO: fix
        }
    }
}

signing {
    sign(publishing.publications["mavenJava"])
}

tasks.javadoc {
    if (JavaVersion.current().isJava9Compatible) {
        (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
    }
}

configure<SpotlessExtension> {
    java {
        googleJavaFormat("1.18.1").aosp().reflowLongStrings().skipJavadocFormatting()
        importOrder()
        removeUnusedImports()

        target(
            "src/*/java/**/*.java"
        )

        targetExclude(
            "src/gen/**",
            "src/genTest/**"
        )
    }
}
