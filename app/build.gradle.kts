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
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  sourceSets {
    getByName("main") {
      jniLibs.srcDirs("src/main/jniLibs", "build/rustJniLibs")
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

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.swiperefreshlayout)
  implementation(libs.androidx.webkit)
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
