plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "edu.cst3492.taide"
version = "0.1.0-b-demo"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    implementation("com.google.code.gson:gson:2.12.1")
    implementation("com.github.javaparser:javaparser-core:3.26.3")
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.1.0.202411261347-r")
    intellijPlatform {
        val localPlatform = providers.gradleProperty("localPlatformPath")
        if (localPlatform.isPresent) local(localPlatform.get()) else intellijIdeaCommunity("2024.2.4")
        bundledPlugin("com.intellij.java")
    }
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
sourceSets {
    main {
        java.setSrcDirs(listOf("../../src/main/java", "../../integration-support/src/main/java"))
        val supportRoot = file("../../integration-support/src/main/java")
        java.exclude {
            !it.isDirectory && it.file.toPath().startsWith(supportRoot.toPath())
                    && file("../../src/main/java/${it.path}").exists()
        }
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
