plugins {
    // Habilita la compilación y el empaquetado de la aplicación Android.
    id("com.android.application") version "9.2.1"
}

android {
    namespace = "com.consultainventario.app"
    // Se compila con el SDK instalado; la app admite teléfonos desde Android 8.
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.consultainventario.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
