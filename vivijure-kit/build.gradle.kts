plugins {
  kotlin("jvm")
  kotlin("plugin.serialization")
  `java-library`
}

group = "org.skyphusion.vivijure"
version = "0.1.0"

dependencies {
  // api: app module uses JsonElement / OkHttp types from kit public surface
  api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
  api("com.squareup.okhttp3:okhttp:4.12.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

  testImplementation(kotlin("test"))
  testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}

tasks.test {
  useJUnitPlatform()
}

kotlin {
  // Host laptop ships JBR 21; CI Temurin 17 also works via auto-detect when present.
  jvmToolchain(21)
}
