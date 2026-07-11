import java.io.FileInputStream
import java.security.KeyStore
import java.util.Collections

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.bote.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bote.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 23
        versionName = "3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // F-Droid: no incluir el bloque de metadatos de dependencias (cifrado con
    // la clave de Google) en el APK; rompe la reproducibilidad y F-Droid lo rechaza.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    // La clave de firma llega por variables de entorno (en CI, desde los secrets del repo).
    signingConfigs {
        create("release") {
            val rutaKeystore = System.getenv("BOTE_KEYSTORE")
            if (rutaKeystore != null) {
                storeFile = file(rutaKeystore)
                storeType = "PKCS12"
                val passAlmacen = System.getenv("BOTE_KEYSTORE_PASS")
                storePassword = passAlmacen
                keyPassword = System.getenv("BOTE_KEY_PASS") ?: passAlmacen
                // Si no se indica alias se usa el de la primera clave del almacén
                // (los PKCS12 exportados por Windows llevan un alias autogenerado).
                keyAlias = System.getenv("BOTE_KEY_ALIAS") ?: run {
                    val almacen = KeyStore.getInstance("PKCS12")
                    FileInputStream(rutaKeystore).use { flujo ->
                        almacen.load(flujo, passAlmacen?.toCharArray())
                    }
                    Collections.list(almacen.aliases())
                        .firstOrNull { alias -> almacen.isKeyEntry(alias) } ?: "bote"
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (System.getenv("BOTE_KEYSTORE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // Dos sabores del mismo código:
    //  - foss  → F-Droid: todo gratis, sin librerías de Google.
    //  - play  → Google Play: la sync se desbloquea por suscripción (BillingClient).
    // Solo cambia la implementación de EntitlementProvider (src/foss vs src/play).
    flavorDimensions += "distribucion"
    productFlavors {
        create("foss") {
            dimension = "distribucion"
        }
        create("play") {
            dimension = "distribucion"
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.2")
    implementation("androidx.activity:activity-ktx:1.9.0")

    // Room (base de datos local)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // WorkManager (sincronización periódica en segundo plano)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Google Play Billing SOLO en el sabor play (no entra en F-Droid).
    "playImplementation"("com.android.billingclient:billing-ktx:6.2.1")

    // Tests unitarios (JVM): Calculadora, Cifrado, Dinero
    testImplementation("junit:junit:4.13.2")

    // Tests instrumentados: migraciones de la base de datos
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")

    // Códigos QR para sincronizar eventos (ZXing, Apache 2.0)
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
