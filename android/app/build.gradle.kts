// The phone app: nx2android's porter, running on the phone itself.
//
// The app is a shell: it unpacks the environment (the rootfs from the nixport payload, the toolchain and the
// tool's own tree) into its files folder, and runs the porter inside it. The UI is Android's own.
plugins {
    id("com.android.application")
}

// The payload (rootfs, toolchain, the tool's tree) is built by packaging/android-apk/build_assets.py and staged
// outside the source tree: it is a few hundred megabytes and not committed.
val assetsDir = rootProject.layout.projectDirectory.dir("../packaging/android-apk/.cache/assets")

android {
    namespace = "com.nx.android"
    compileSdk = 37

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

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")   // a release build is signed by the build's own key
        }
    }

    dependencies {
        testImplementation("junit:junit:4.13.2")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
