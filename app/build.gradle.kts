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
  compileSdk = 36

  defaultConfig {
    applicationId = "com.aistudio.edgellm.qvmxrp"
    minSdk = 24
    targetSdk = 36
    val buildNumber = (System.getenv("BUILD_NUMBER") ?: System.getenv("GITHUB_RUN_NUMBER"))?.toIntOrNull() ?: 1
    versionCode = buildNumber
    versionName = System.getenv("APP_VERSION_NAME") ?: "1.0.$buildNumber"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    ndk {
      abiFilters += listOf("arm64-v8a", "x86_64")
    }
  }

  flavorDimensions += "backend"
  productFlavors {
    create("cpu") {
      dimension = "backend"
      buildConfigField("boolean", "GGML_VULKAN_COMPILED", "false")
    }
    create("vulkan") {
      dimension = "backend"
      applicationIdSuffix = ".vulkan"
      versionNameSuffix = "-vulkan"
      minSdk = 29
      buildConfigField("boolean", "GGML_VULKAN_COMPILED", "true")
      externalNativeBuild {
        cmake {
          arguments += "-DEDGELLM_GPU_VULKAN=ON"
          val spirvDir = System.getenv("SPIRV_HEADERS_DIR")?.takeIf { it.isNotBlank() }
            ?: "/usr/local/share/cmake/SPIRV-Headers"
          arguments += "-DSPIRV-Headers_DIR=$spirvDir"
          arguments += "-DCMAKE_CXX_FLAGS=-I/tmp/vkinc"
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
      } else if (System.getenv("ALLOW_DEBUG_SIGNED_RELEASE") == "true") {
        storeFile = file("${rootDir}/debug.keystore")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      } else {
        throw GradleException(
          "Release keystore not found at $keystorePath. Provide a real upload key " +
            "via KEYSTORE_PATH (+ STORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD), or set " +
            "ALLOW_DEBUG_SIGNED_RELEASE=true for CI-only debug-signed artifacts."
        )
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

secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

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
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.converter.moshi)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
}
