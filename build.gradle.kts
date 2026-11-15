plugins {
    `java-library`
    `maven-publish`
    signing
    jacoco
}

group = "io.github.xxvw"
version = providers.gradleProperty("version").getOrElse("0.1.0")

val targetJavaVersion = providers.gradleProperty("javaVersion").getOrElse("17").toInt()
require(targetJavaVersion >= 17) { "javaVersion must be 17 or newer" }

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    }
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.compilerArgs.addAll(listOf("-Xlint:all"))
    options.encoding = "UTF-8"
}

tasks.compileTestJava {
    modularity.inferModulePath = false
}

tasks.test {
    useJUnitPlatform()
    modularity.inferModulePath = false
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).apply {
        addBooleanOption("Xdoclint:all,-missing", true)
        encoding("UTF-8")
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name = "vrc-osc-java"
                description = "A Java 17 OSC library with first-class VRChat helpers."
                url = "https://github.com/xxvw/vrc-osc-java"

                licenses {
                    license {
                        name = "MIT License"
                        url = "https://opensource.org/licenses/MIT"
                        distribution = "repo"
                    }
                }

                developers {
                    developer {
                        id = "xxvw"
                        name = "xxvw"
                        url = "https://github.com/xxvw"
                    }
                }

                scm {
                    connection = "scm:git:https://github.com/xxvw/vrc-osc-java.git"
                    developerConnection = "scm:git:ssh://git@github.com/xxvw/vrc-osc-java.git"
                    url = "https://github.com/xxvw/vrc-osc-java"
                }

                issueManagement {
                    system = "GitHub Issues"
                    url = "https://github.com/xxvw/vrc-osc-java/issues"
                }
            }
        }
    }
}

val signingKeyValue = providers.environmentVariable("SIGNING_KEY")
    .orElse(providers.gradleProperty("signingKey"))
    .orNull
val signingPasswordValue = providers.environmentVariable("SIGNING_PASSWORD")
    .orElse(providers.gradleProperty("signingPassword"))
    .orNull

signing {
    isRequired = false
    if (signingKeyValue != null && signingPasswordValue != null) {
        useInMemoryPgpKeys(signingKeyValue, signingPasswordValue)
        sign(publishing.publications)
    }
}
