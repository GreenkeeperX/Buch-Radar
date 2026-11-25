import java.security.MessageDigest
import java.net.URI
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
val localSettings = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val momoxToken = providers.environmentVariable("MOMOX_API_TOKEN").orNull ?: localSettings.getProperty("momox.apiToken", "")
// Private builds only; public release APKs are built with an empty key like the Momox token.
val googleBooksKey = providers.environmentVariable("GOOGLE_BOOKS_API_KEY").orNull ?: localSettings.getProperty("googleBooks.apiKey", "")
if (momoxToken.isNotBlank() || googleBooksKey.isNotBlank())
    logger.lifecycle("Hinweis: Dieser Build enthält private Zugangswerte (Momox/Google Books). Die APK nicht weitergeben oder veröffentlichen.")

android {
    namespace = "de.buchradar.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "de.buchradar.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 27
        versionName = "0.15.0"
        buildConfigField("String", "MOMOX_API_TOKEN", "\"${momoxToken.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        buildConfigField("String", "GOOGLE_BOOKS_API_KEY", "\"${googleBooksKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // OpenCV (cover confirmation) ships large native libraries per ABI; the app targets phones only.
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    buildTypes {
        // Device tests run as a separate app id. The Android test runner uninstalls the
        // tested app after `connected…AndroidTest`; it must never touch the real app
        // `de.buchradar.app` and its local book list.
        create("deviceTest") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".devicetest"
            versionNameSuffix = "-devicetest"
            matchingFallbacks += listOf("debug")
        }
    }
    testBuildType = "deviceTest"
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isReturnDefaultValues = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    // Android 16 removed the reflective InputManager API used by older Espresso.
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    "deviceTestImplementation"("androidx.compose.ui:ui-test-manifest")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Apache-2.0; ORB features and RANSAC homography for cover confirmation.
    implementation("org.opencv:opencv:4.13.0")
    // Apache-2.0; fallback reader for Fraktur and script titles (single book, only when needed).
    implementation("com.github.adaptech-cz.Tesseract4Android:tesseract4android:4.8.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
    implementation("net.creativecouple.validation:fast-isbn:1.2.47")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

// Tesseract models (Apache-2.0, tesseract-ocr/tessdata_best and tessdata_fast) are downloaded at build time
// with fixed checksums instead of being committed.
val tessModels = mapOf(
    "deu_latf.traineddata" to ("https://github.com/tesseract-ocr/tessdata_best/raw/main/deu_latf.traineddata" to "429b8c33d37e1a4823a2bb082a644ff13e252f735b7945e08a518d22ddcc459e"),
    "deu.traineddata" to ("https://github.com/tesseract-ocr/tessdata_best/raw/main/deu.traineddata" to "8407331d6aa0229dc927685c01a7938fc5a641d1a9524f74838cdac599f0d06e"),
    "deu_fast.traineddata" to ("https://github.com/tesseract-ocr/tessdata_fast/raw/main/deu.traineddata" to "19d219bbb6672c869d20a9636c6816a81eb9a71796cb93ebe0cb1530e2cdb22d")
)
abstract class DownloadTessModels : DefaultTask() {
    @get:Input abstract val models: MapProperty<String, String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty
    @TaskAction fun download() {
        val dir = outputDir.get().dir("tessdata").asFile.apply { mkdirs() }
        models.get().forEach { (name, spec) ->
            val (url, checksum) = spec.split(" ")
            val target = File(dir, name)
            fun sha(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            if (!target.exists() || sha(target) != checksum) {
                URI(url).toURL().openStream().use { input -> target.outputStream().use { input.copyTo(it) } }
                check(sha(target) == checksum) { "Prüfsumme von $name stimmt nicht" }
            }
        }
    }
}
val downloadTessModels = tasks.register<DownloadTessModels>("downloadTessModels") {
    models.set(tessModels.mapValues { (_, source) -> "${source.first} ${source.second}" })
}
androidComponents {
    onVariants { variant -> variant.sources.assets?.addGeneratedSourceDirectory(downloadTessModels, DownloadTessModels::outputDir) }
}
