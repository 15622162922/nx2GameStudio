// The phone app: nx2android's porter, running on the phone itself.
//
// The app is a shell: it unpacks the environment (the rootfs from the nixport payload, the toolchain and the
// tool's own tree) into its files folder, and runs the porter inside it. The UI is Android's own.
plugins {
    id("com.android.application")
    // The Compose compiler: from Kotlin 2.0 it is a plugin of its own
    id("org.jetbrains.kotlin.plugin.compose")
}

// The payload (rootfs, toolchain, the tool's tree) is built by packaging/android-apk/build_assets.py and staged
// outside the source tree: it is a few hundred megabytes and not committed.
val assetsDir = rootProject.layout.projectDirectory.dir("../packaging/android-apk/.cache/assets")

// The app's own signing key: a keystore of the project's, so that every build of the app (this machine, CI, the
// release) carries the same signature and installs over the one before it. Android refuses an update whose
// signature differs (INSTALL_FAILED_UPDATE_INCOMPATIBLE), and the debug keystore of a machine is that machine's:
// two builds from two machines can never replace each other. The keystore is not in the repository: it lives in
// ~/.android/nx2android.keystore (or wherever NX2A_KEYSTORE points), and CI gets it from the secret
// NX2A_KEYSTORE_B64. Without it the build falls back to the machine's debug key, which is good enough for a
// build nobody installs over another.
fun envOf(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }
val nx2aKeystore = File(envOf("NX2A_KEYSTORE") ?: "${System.getProperty("user.home")}/.android/nx2android.keystore")

android {
    namespace = "com.nx.android"
    compileSdk = 37

    signingConfigs {
        if (nx2aKeystore.isFile) {
            create("nx2android") {
                storeFile = nx2aKeystore
                storePassword = envOf("NX2A_KEYSTORE_PASSWORD") ?: "nx2android"
                keyAlias = envOf("NX2A_KEYSTORE_ALIAS") ?: "nx2android"
                keyPassword = envOf("NX2A_KEY_PASSWORD") ?: envOf("NX2A_KEYSTORE_PASSWORD") ?: "nx2android"
            }
        }
    }

    defaultConfig {
        applicationId = "com.nx.android"
        minSdk = 26
        // SDK 29 from the platform enforces W^X for app data: the guest programs under files/rootfs could not
        // be exec'd at all. The app is not on Google Play (it ships as an APK from the project's releases).
        targetSdk = 28
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    sourceSets {
        getByName("main") {
            // The payload (rootfs, toolchain, the tool's tree): built by packaging/android-apk/build_assets.py.
            // -PnoPayload leaves it out: a quick build of the app itself, for a phone that already has the
            // environment unpacked (the unpacked tree is in the app's own folder, so it survives the update).
            if (!project.hasProperty("noPayload") && assetsDir.asFile.exists()) {
                assets.directories.add(assetsDir.asFile.absolutePath)
            }
        }
    }

    // The payload is big: no compression of the assets that are already compressed (the rootfs tarball), and
    // the whole thing is a single big blob the app unpacks on the first start.
    androidResources {
        noCompress += listOf("gz", "so")
    }
    packaging {
        jniLibs.useLegacyPackaging = true
    }

    // The screen is Jetpack Compose (Material 3).
    buildFeatures {
        compose = true
    }

    buildTypes {
        // The project's own key when it is there (the machine's, or the one CI restores from the secret), the
        // machine's debug key otherwise: the app is not on Google Play, and a build nobody installs over another
        // needs no key of its own.
        getByName("debug") { signingConfig = signingConfigs.findByName("nx2android") ?: signingConfigs.getByName("debug") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("nx2android") ?: signingConfigs.getByName("debug")
        }
    }

    dependencies {
        implementation(platform("androidx.compose:compose-bom:2026.09.00"))
        implementation("androidx.compose.ui:ui")
        implementation("androidx.compose.ui:ui-tooling-preview")
        implementation("androidx.compose.material3:material3")
        implementation("androidx.compose.material:material-icons-extended")
        implementation("androidx.activity:activity-compose:1.13.0")
        testImplementation("junit:junit:4.13.2")
        // the porter's progress events and the data pack's manifest are JSON: the JVM tests parse them with
        // the real org.json (the android.jar the tests run against only has the stubs)
        testImplementation("org.json:json:20240303")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
