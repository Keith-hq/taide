plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "edu.cst3492.taide"
version = "0.1.0-b-demo"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.2.4")
        bundledPlugin("com.intellij.java")
    }
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
sourceSets {
    main {
        java.setSrcDirs(listOf("../../src/main/java", "../../integration-support/src/main/java"))
        resources.setSrcDirs(listOf("../../src/main/resources"))
    }
}
intellijPlatform {
    pluginConfiguration {
        ideaVersion { sinceBuild.set("242"); untilBuild.set("243.*") }
    }
    buildSearchableOptions.set(false)
}
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}
