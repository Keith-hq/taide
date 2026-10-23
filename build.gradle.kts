import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import java.time.Duration

plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.12.1")
    implementation("com.github.javaparser:javaparser-core:3.26.3")
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.1.0.202411261347-r")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.apiguardian:apiguardian-api:1.1.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
    testImplementation("org.junit.platform:junit-platform-launcher")

    intellijPlatform {
        val localPlatform = providers.gradleProperty("localPlatformPath")
        if (localPlatform.isPresent) {
            local(localPlatform.get())
        } else {
            intellijIdeaCommunity(providers.gradleProperty("platformVersion"))
        }
        bundledPlugin("com.intellij.java")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
        pluginVerifier()
        zipSigner()
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// 正式入口由 B 维护；独立沙箱使用相同的动作注册片段。
sourceSets.main {
    if (!file("src/main/resources/META-INF/plugin.xml").exists()) {
        resources.srcDir("src/standalone/resources")
    }
}

intellijPlatform {
    pluginConfiguration {
        name = "TAIDE"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "242"
            untilBuild = "242.*"
        }
    }
    pluginVerification {
        ides {
            val localPlatform = providers.gradleProperty("localPlatformPath")
            if (localPlatform.isPresent) {
                local(localPlatform.get())
            } else {
                ide(IntelliJPlatformType.IntellijIdeaCommunity, providers.gradleProperty("platformVersion").get())
            }
        }
    }
}

val testLauncher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(21)
}
val testCompiler = javaToolchains.compilerFor {
    languageVersion = JavaLanguageVersion.of(21)
}
val liveModelTests = providers.gradleProperty("liveModelTests").map(String::toBoolean).orElse(false)

tasks {
    register("validateReleaseVersion") {
        doLast {
            val tag = providers.environmentVariable("GITHUB_REF_NAME").getOrElse("")
            require(!project.version.toString().endsWith("-SNAPSHOT") && tag == "v${project.version}") {
                "发布标签须为 v<pluginVersion>，并使用正式版本号。"
            }
        }
    }
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 21
    }
    test {
        useJUnitPlatform {
            if (!liveModelTests.get()) {
                excludeTags("live-model")
            }
        }
        outputs.upToDateWhen { !liveModelTests.get() }
        outputs.doNotCacheIf("真实模型验收需要重新调用服务") { liveModelTests.get() }
        if (liveModelTests.get()) {
            reports.html.outputLocation.set(layout.buildDirectory.dir("reports/tests/live-model"))
            reports.junitXml.outputLocation.set(layout.buildDirectory.dir("test-results/live-model"))
        }
        javaLauncher.set(testLauncher)
        systemProperty("taide.test.javac", testCompiler.get().executablePath.asFile.absolutePath)
        systemProperty("java.awt.headless", "true")
        maxHeapSize = "1536m"
        timeout.set(Duration.ofMinutes(if (liveModelTests.get()) 15 else 5))
        testLogging {
            events("failed", "skipped")
        }
    }
    wrapper {
        gradleVersion = "8.13"
        distributionType = Wrapper.DistributionType.BIN
    }
}
