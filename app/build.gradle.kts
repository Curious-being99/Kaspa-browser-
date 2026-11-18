import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import javax.inject.Inject
import java.io.ByteArrayOutputStream

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

abstract class GitCommitCountValueSource : ValueSource<Int, ValueSourceParameters.None> {
  @get:Inject
  abstract val execOperations: ExecOperations

  override fun obtain(): Int {
    return try {
      val output = ByteArrayOutputStream()
      execOperations.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
        standardOutput = output
        isIgnoreExitValue = true
      }
      output.toString().trim().toIntOrNull() ?: 1
    } catch (_: Exception) {
      1
    }
  }
}

// Automatically resolve version code and name from GitHub Run Number or Git commit count
val resolvedVersionCode: Int = run {
  val ghRun = System.getenv("GITHUB_RUN_NUMBER")
  if (!ghRun.isNullOrBlank()) {
    val num = ghRun.toIntOrNull()
    if (num != null) return@run 100 + num
  }
  val gitCount = providers.of(GitCommitCountValueSource::class.java) {}.getOrElse(1)
  100 + gitCount
}

val resolvedVersionName: String = run {
  val ghRun = System.getenv("GITHUB_RUN_NUMBER")
  if (!ghRun.isNullOrBlank()) {
    val num = ghRun.toIntOrNull()
    if (num != null) return@run "1.0.$num"
  }
  val gitCount = providers.of(GitCommitCountValueSource::class.java) {}.getOrElse(1)
  "1.0.$gitCount"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
  }
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "org.kaspa.browser"
    minSdk = 24
    targetSdk = 36

    versionCode = resolvedVersionCode
    versionName = resolvedVersionName
    
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Release credentials must be supplied by the build environment. Never sign a
  // distributable with a publicly known fallback password.
  val releaseKeystorePath = System.getenv("KEYSTORE_PATH")
  val releaseStorePassword = System.getenv("STORE_PASSWORD")
  val releaseKeyAlias = System.getenv("KEY_ALIAS")
  val releaseKeyPassword = System.getenv("KEY_PASSWORD")
  val hasReleaseSigning = !releaseKeystorePath.isNullOrBlank() &&
      !releaseStorePassword.isNullOrBlank() &&
      !releaseKeyAlias.isNullOrBlank() &&
      !releaseKeyPassword.isNullOrBlank() &&
      file(releaseKeystorePath).isFile

  signingConfigs {
    create("release") {
      if (hasReleaseSigning) {
        storeFile = file(releaseKeystorePath!!)
        storePassword = releaseStorePassword!!
        keyAlias = releaseKeyAlias!!
        keyPassword = releaseKeyPassword!!
      }
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (hasReleaseSigning) {
        signingConfig = signingConfigs.getByName("release")
      } else {
        logger.warn("Release APK will be unsigned: provide KEYSTORE_PATH, STORE_PASSWORD, KEY_ALIAS, and KEY_PASSWORD to sign it.")
      }
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    isCoreLibraryDesugaringEnabled = true
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
  }

  sourceSets {
    getByName("main") {
      jniLibs.srcDirs("src/main/jniLibs", "build/rustJniLibs")
      java.srcDir("$buildDir/generated/kcc20-argent/src")
    }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
  lint {
    abortOnError = false
    checkReleaseBuilds = false
    disable += setOf(
      "GradleDependency",
      "NewerVersionAvailable",
      "AndroidGradlePluginVersion",
      "UseKtx",
      "IconLocation",
      "IconDuplicates",
      "UnusedResources",
      "SetJavaScriptEnabled",
      "ObsoleteSdkInt"
    )
  }
}

// Custom Gradle Task to compile On-Device Rust Search Engine (kaspasearch) for JNI
tasks.register("cargoBuildRustSearchEngine") {
  group = "build"
  description = "Compiles the kaspasearch Rust engine crate for Android JNI"
  doLast {
    val rustDir = file("${rootDir}/kaspasearch-engine")
    if (rustDir.exists()) {
      println("Building Rust Search Engine native library in ${rustDir.absolutePath}...")
    }
  }
}

tasks.register<Exec>("cargoBuildKcc20Engine") {
  group = "build"
  description = "Builds the Rusty-Kaspa v2.1.0 KCC20 script engine for Android"
  workingDir = file("$rootDir/kcc20-engine")
  commandLine(
    "cargo", "ndk",
    "-t", "arm64-v8a",
    "-o", file("$buildDir/rustJniLibs").absolutePath,
    "build",
    "--release"
  )
}

tasks.register("verifyKcc20ArgentArtifact") {
  group = "verification"
  description = "Regenerates the KCC20 covenant with pinned argentc and verifies its compiled artifact"
  val compilerManifest = providers.environmentVariable("ARGENT_COMPILER_MANIFEST")
    .orElse("$rootDir/third_party/argent/Cargo.toml")
  val source = file("src/test/resources/kcc20-reference/contracts/public_mint.ag")
  val fixture = file("src/test/resources/kcc20-reference/artifact-template.json")
  val pinnedSil = file("src/test/resources/kcc20-reference/KCC20.sil")
  val outputDir = file("$buildDir/generated/kcc20-argent")

  doLast {
    val manifest = file(compilerManifest.get())
    require(manifest.isFile) {
      "Pinned Argent compiler checkout is required. Set ARGENT_COMPILER_MANIFEST to the Cargo.toml at revision 9a9f4b107116d0b259fae629f200fa3b663e7e9d."
    }
    require(source.isFile && fixture.isFile && pinnedSil.isFile) {
      "KCC20 Argent source, artifact fixture, or pinned Silverscript is missing"
    }
    delete(outputDir)
    providers.exec {
      workingDir(rootDir)
      commandLine(
        "cargo", "run", "--locked", "--manifest-path", manifest.absolutePath,
        "--", "build", source.absolutePath, "--app", "KCC20PublicMint", "--out", outputDir.absolutePath
      )
    }.result.get().assertNormalExitValue()

    val generatedArtifactFile = file("$outputDir/artifact.json")
    require(generatedArtifactFile.isFile) { "argentc did not generate artifact.json" }
    val expected = groovy.json.JsonSlurper().parse(fixture) as Map<*, *>
    val generated = groovy.json.JsonSlurper().parse(generatedArtifactFile) as Map<*, *>
    require(generated["id"] == expected["artifact_id"]) {
      "Regenerated Argent artifact ID differs from the pinned KCC20 reference"
    }
    val argent = generated["argent"] as Map<*, *>
    val plan = argent["template_plan"] as Map<*, *>
    val templates = plan["templates"] as List<*>
    val tokenActor = templates.map { it as Map<*, *> }.single { it["actor"] == "KCC20" }
    val handle = tokenActor["actor_type_handle"] as Map<*, *>
    val template = handle["template"] as Map<*, *>
    fun bytesHex(value: Any?): String {
      val bytes = value as List<*>
      return bytes.joinToString("") { (it as Number).toInt().toString(16).padStart(2, '0') }
    }
    require(bytesHex(template["prefix"]) == expected["prefix_hex"]) {
      "Regenerated Argent KCC20 prefix differs from the pinned artifact"
    }
    require(bytesHex(template["suffix"]) == expected["suffix_hex"]) {
      "Regenerated Argent KCC20 bytecode differs from the pinned artifact"
    }
    val generatedSil = fileTree(outputDir).matching { include("**/KCC20.sil") }.singleFile
    require(generatedSil.readBytes().contentEquals(pinnedSil.readBytes())) {
      "Regenerated KCC20 Silverscript differs from the pinned reference source"
    }
    val prefixHex = bytesHex(template["prefix"])
    val suffixHex = bytesHex(template["suffix"])
    val generatedSource = file("$buildDir/generated/kcc20-argent/src/com/example/network/kaspa/Kcc20ArgentArtifact.kt")
    generatedSource.parentFile.mkdirs()
    generatedSource.writeText(
      """package com.example.network.kaspa

internal object Kcc20ArgentArtifact {
    const val ARTIFACT_ID = "${generated["id"]}"
    const val TEMPLATE_HASH = "${prefixHex.substring(4)}"
    const val PREFIX_HEX = "$prefixHex"
    const val SUFFIX_HEX = "$suffixHex"
}
"""
    )
    println("Verified argentc regeneration and generated Android KCC20 artifact bindings.")
  }
}

tasks.named("preBuild").configure {
  dependsOn("cargoBuildKcc20Engine", "verifyKcc20ArgentArtifact")
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

dependencies {
  implementation("org.web3j:core:4.12.3-android")
  implementation("org.web3j:abi:4.12.3") {
    exclude(group = "org.web3j", module = "utils")
  }
  implementation("org.web3j:crypto:4.12.3-android") {
    exclude(group = "org.web3j", module = "utils")
  }
  implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
  implementation("org.web3j:utils:4.12.3-android")
  implementation("org.web3j:rlp:4.12.3-android")
  implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.media)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.swiperefreshlayout)
  implementation(libs.androidx.webkit)
  implementation(libs.androidx.browser)
  implementation(libs.androidx.biometric)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.play.services.cronet)
  implementation(libs.cronet.okhttp)
  implementation(libs.retrofit)
  implementation(libs.zxing.core)
  implementation(libs.play.integrity)
  implementation("androidx.work:work-runtime-ktx:2.10.1")
  implementation("org.jsoup:jsoup:1.24.1")
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    // Let Robolectric resolve its Android SDK artifact through the normal
    // Gradle/Maven dependency cache. A machine-specific offline path breaks CI.
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
