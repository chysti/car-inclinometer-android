plugins { id("com.android.application") }

android {
    namespace = "com.stakan.carinclinometer"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.stakan.carinclinometer"
        minSdk = 23
        targetSdk = 35
        versionCode = 6
        versionName = "1.1.1"
    }
    signingConfigs {
        create("releaseUpload") {
            storeFile = rootProject.file("car-inclinometer-upload.jks")
            storePassword = rootProject.file("upload-key-password.txt").readText().trim()
            keyAlias = "glass-blocks-upload"
            keyPassword = storePassword
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("releaseUpload")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies { }
