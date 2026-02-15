plugins {
    id("java")
    id("com.gradleup.shadow") version "8.3.6"
}

group = "com.jher235"
version = "1.0.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {

    // Java Parser
    implementation("com.github.javaparser:javaparser-symbol-solver-core:3.26.2")

    // CLI
    implementation("info.picocli:picocli:4.7.6")
    annotationProcessor("info.picocli:picocli-codegen:4.7.6")

    // JGit
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.1.0.202411261347-r")

    // Test
    testImplementation(platform("org.junit:junit-bom:5.11.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    testImplementation("org.assertj:assertj-core:3.26.3")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.jher235.jfocus.cli.JFocusCli"
    }
}

tasks.shadowJar {
    manifest {
        attributes["Main-Class"] = "com.jher235.jfocus.cli.JFocusCli"
    }
}


tasks.test {
    useJUnitPlatform()
}