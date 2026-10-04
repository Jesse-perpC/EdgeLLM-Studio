import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.perpcorp.edgellm"
  ndkVersion = "27.3.13750724"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.edgellm.qvmxrp"
    minSdk = 24
    targetSdk = 36
    val buildNumber = (System.getenv("BUILD_NUMBER") ?: System.getenv("GITHUB_RUN_NUMBER"))?.toIntOrNull() ?: 1
    versionCode = buildNumber
    versionName = System.getenv("APP_VERSION_NAME") ?: "1.0.$buildNumber"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    ndk {
      // llama.cpp reference kernels target NEON-capable ARM64; keep x86_64 for emulator runs
      abiFilters += listOf("arm64-v8a", "x86_64")
    }
  }

  flavorDimensions += "backend"
  productFlavors {
    // Default production flavor: CPU (ARM NEON) ggml backend. Builds from a
    // stock NDK with no extra SDKs and runs on every ARM64 device.
    create("cpu") {
      dimension = "backend"
      buildConfigField("boolean", "GGML_VULKAN_COMPILED", "false")
    }
    // GPU flavor: compiles the ggml Vulkan backend (needs glslc + Vulkan
    // headers, installed in CI). Install side-by-side with the cpu flavor via
    // the applicationId suffix. Falls back to CPU at runtime on devices
    // without a Vulkan driver — see LlamaCppEngine.resolveGpuLayers.
    create("vulkan") {
      dimension = "backend"
      applicationIdSuffix = ".vulkan"
      versionNameSuffix = "-vulkan"
      buildConfigField("boolean", "GGML_VULKAN_COMPILED", "true")
      externalNativeBuild {
        cmake {
          arguments += "-DEDGELLM_GPU_VULKAN=ON"
        }
      }
    }
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      val releaseKeystore = file(keystorePath)
      if (releaseKeystore.exists()) {
        storeFile = releaseKeystore
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      } else {
        // Safe fallback to debug.keystore when custom release key is not supplied
        // Guarantees assembleRelease builds and signs successfully in CI without breaking the workflow
        storeFile = file("${rootDir}/debug.keystore")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
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
      signingConfig = signingConfigs.getByName("release")
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
  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
      version = "3.22.1"
    }
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      isReturnDefaultValues = true
    }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // Cloud Hub (opt-in): hardware-backed EncryptedSharedPreferences for third-party API keys.
  // No cloud call is ever made unless the user configures a key AND selects cloud mode.
  implementation(libs.security.crypto)
  // Real on-device weight inference for Gemma/LiteRT .task models (MediaPipe LLM Inference API).
  // If manifest merger ever reports a minSdk conflict from this AAR, raise app minSdk to 26.
  implementation(libs.mediapipe.tasks.genai)
  // Real ONNX Runtime for .onnx decoder models. Ships CPU + NNAPI + XNNPACK.
  // Replaces the previous ONNX branch, which produced canned text because no
  // ONNX runtime was ever on the classpath.
  implementation(libs.onnxruntime.android)
  // Real TensorFlow Lite runtime for .tflite classifiers (MobileBERT).
  // Replaces the path that answered classification prompts from the
  // hardcoded knowledge engine.
  implementation(libs.tensorflow.lite)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
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
