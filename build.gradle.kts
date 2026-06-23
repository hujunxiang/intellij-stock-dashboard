import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.Constants
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.intellij.platform.gradle.providers.ProductReleasesValueSource
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

fun properties(key: String) = project.findProperty(key).toString()

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.21"
    id("org.jetbrains.intellij.platform") version "2.16.0"
    id("org.jetbrains.changelog") version "2.5.0"
}

group = properties("pluginGroup")
version = properties("pluginVersion")

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

dependencies {
    implementation("org.apache.commons:commons-text:1.14.0")
    implementation("com.belerweb:pinyin4j:2.5.1")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // The IntelliJ Platform on the test classpath references JUnit 4's TestRule, which the
    // 2025.2+ platform no longer provides transitively. Our own tests run on JUnit 5 via
    // useJUnitPlatform(); this only satisfies the platform's runtime reference.
    testRuntimeOnly("junit:junit:4.13.2")
    intellijPlatform {
        create(properties("platformType"), properties("platformVersion"))
        pluginVerifier()
    }
}

tasks.test {
    useJUnitPlatform()
}

changelog {
    version.set(properties("pluginVersion"))
    path.set("${project.projectDir}/CHANGELOG.md")
    groups.set(emptyList())
}

val pluginDescription = """
    <div>
      <p>
        StockerPlus is a JetBrains IDE extension dashboard for investors to track
        real-time stock market conditions.
      </p>
      <h2>Features</h2>
      <ul>
        <li>- 📊 **Real-time Market Data** - Track stocks and cryptocurrencies with live updates</li>
        <li>- 🎨 **Customizable Display** - Choose from multiple color patterns and customize visible table columns</li>
        <li>- 🔤 **Pinyin Support** - Display stock names in Pinyin for easier reading</li>
        <li>- 📈 **Sortable Columns** - Three-state sorting (ascending, descending, unsorted) on any column</li>
        <li>- 🪙 **Cryptocurrency Support** - Monitor crypto assets alongside traditional securities</li>
        <li>- 🎯 **Custom Stock Names** - Set custom names for your favorite stocks</li>
        <li>- 🔍 **Smart Search** - Quickly find and add stocks with intelligent search dialog</li>
        <li>- 📋 **Batch Operations** - Manage multiple stocks at once with batch add and delete and reorder</li>
        <li>- 💾 **Persistent Settings** - Your preferences and watchlist are saved across IDE sessions</li>
        <li>- 🌐 **F10 Stock Detail** - Open stock detail page in browser with F10 key</li>
        <li>- 📦 **Stock Groups Operations** - Manage stock groups</li>
      </ul>
      <h2>Quick Start</h2>
      <ol>
        <li>Open the Stocker tool window from the sidebar</li>
        <li>Click "Add Favorite Stocks" to search and add stocks</li>
        <li>Customize settings at Settings → Tools → Stocker</li>
        <li>Track your investments in real-time!</li>
      </ol>
   
    </div>
""".trimIndent()

intellijPlatform {
    buildSearchableOptions = false
    pluginConfiguration {
        name = properties("pluginName")
        version = properties("pluginVersion")
        description = pluginDescription
        changeNotes = provider {
            changelog.renderItem(changelog.getLatest(), Changelog.OutputType.HTML)
        }
        ideaVersion {
            untilBuild = provider { null }
        }
    }
    publishing {
        token = System.getProperty("jetbrains.token")
    }
    pluginVerification {
        ides {
            recommended()
            select {
                types = listOf(IntelliJPlatformType.IntellijIdeaUltimate)
                channels = listOf(ProductRelease.Channel.RELEASE)
                sinceBuild = "252"
            }
        }
        failureLevel = listOf(
            VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS, VerifyPluginTask.FailureLevel.INVALID_PLUGIN
        )
    }
}

// Resolve the latest stable IntelliJ IDEA *release* version (RELEASE channel only, so
// EAP/beta builds are excluded). The feed returns coordinates such as "IU-2026.1.3",
// ordered newest-first; we take the newest and strip the product-code prefix to get the
// bare version (e.g. "2026.1.3"). We query the Ultimate product because it is always
// published and shares release versions with the unified IDEA distribution used below.
// Resolution is lazy and only happens when the runIdeLatest task actually runs.
val latestIdeaReleaseVersion = providers.of(ProductReleasesValueSource::class) {
    parameters {
        jetbrainsIdesUrl = Constants.Locations.PRODUCTS_RELEASES_JETBRAINS_IDES
        androidStudioUrl = Constants.Locations.PRODUCTS_RELEASES_ANDROID_STUDIO
        channels = listOf(ProductRelease.Channel.RELEASE)
        types = listOf(IntelliJPlatformType.IntellijIdeaUltimate)
        sinceBuild = "252"
        untilBuild = "999.*"
    }
}.map { releases -> releases.first().substringAfter('-') }

// `./gradlew runIdeLatest` launches a sandbox IDE on the latest stable IntelliJ IDEA
// release instead of the build target declared in gradle.properties (platformVersion).
// Uses the unified IntelliJ IDEA distribution (IntellijIdea), since the separate
// Community (IC) artifact is no longer published as of 2025.3 / build 253.
intellijPlatformTesting {
    runIde {
        register("runIdeLatest") {
            type = IntelliJPlatformType.IntellijIdea
            version = latestIdeaReleaseVersion
        }
    }
}
