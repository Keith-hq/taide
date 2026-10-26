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

    // A（RAG 摄取/切块）：PDFBox 逐页解析 + POI XSLF 逐片解析。合并前请 C 审阅打包排除。
    implementation("org.apache.pdfbox:pdfbox:3.0.5")
    implementation("org.apache.poi:poi-ooxml:5.4.1")
    implementation("org.apache.logging.log4j:log4j-api:2.24.3")
    // A（RAG 嵌入）：ONNX Runtime 本地跑 MiniLM；模型文件不进插件包（运行时按需下载）。
    implementation("com.microsoft.onnxruntime:onnxruntime:1.20.0")

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
    val supportRoot = file("integration-support/src/main/java")
    java.srcDir(supportRoot)
    java.exclude {
        !it.isDirectory && it.file.toPath().startsWith(supportRoot.toPath())
                && file("src/main/java/${it.path}").exists()
    }
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

tasks.withType<org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask>().configureEach {
    val httpsHost = System.getProperty("https.proxyHost")
    if (httpsHost != null) {
        systemProperty("https.proxyHost", httpsHost)
        systemProperty("https.proxyPort", System.getProperty("https.proxyPort", "443"))
    }
    val httpHost = System.getProperty("http.proxyHost")
    if (httpHost != null) {
        systemProperty("http.proxyHost", httpHost)
        systemProperty("http.proxyPort", System.getProperty("http.proxyPort", "80"))
    }
    if (gradle.startParameter.isOffline) {
        offline.set(true)
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
    // A-1 验收 CLI：解析课件目录并打印分块统计与抽样（A 所有，C 审阅）。
    register<JavaExec>("ingestMaterials") {
        group = "verification"
        description = "Ingest a course materials directory and print chunk stats + samples"
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set("edu.cst3492.taide.rag.ingest.IngestMain")
    }
    // A-2 验收 CLI：构建索引并可选查询（模型目录用 TAIDE_MODEL_DIR 环境变量指定）。
    register<JavaExec>("buildIndex") {
        group = "verification"
        description = "Build the course index from a materials directory and optionally query it"
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set("edu.cst3492.taide.rag.index.BuildIndexMain")
        systemProperty("taide.model.dir", providers.environmentVariable("TAIDE_MODEL_DIR").getOrElse(""))
    }
    // A-3 评测 CLI：建索引后跑 20 题评测集，打印逐题 top-1 与命中率。
    register<JavaExec>("evalRetrieval") {
        group = "verification"
        description = "Run the 20-question retrieval eval set and print hit rates"
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set("edu.cst3492.taide.rag.retrieve.RetrievalEvalMain")
        systemProperty("taide.model.dir", providers.environmentVariable("TAIDE_MODEL_DIR").getOrElse(""))
    }
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
        systemProperty("taide.model.dir", providers.environmentVariable("TAIDE_MODEL_DIR").getOrElse(""))
        systemProperty("taide.materials.dir", providers.environmentVariable("TAIDE_MATERIALS_DIR").getOrElse(""))
        systemProperty("taide.llm.api.key", providers.environmentVariable("TAIDE_LLM_API_KEY").getOrElse(""))
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
