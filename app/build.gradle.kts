plugins {
  id("com.android.application")
  kotlin("android")
}

android {
  namespace = "org.skyphusion.vivijure"
  compileSdk = 35

  defaultConfig {
    applicationId = "org.skyphusion.vivijure"
    minSdk = 26
    targetSdk = 35
    versionCode = 1
    versionName = "0.0.1"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions {
    jvmTarget = "17"
  }
  buildFeatures {
    compose = false
  }
}

dependencies {
  implementation(project(":vivijure-kit"))
}
