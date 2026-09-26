plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.vanniktech.maven.publish") version "0.30.0"
}

val sdkVersion = "0.1.3"

android {
    namespace = "co.rivium.chat"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

mavenPublishing {
    publishToMavenCentral(
        com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL,
        automaticRelease = true
    )
    signAllPublications()

    coordinates("co.rivium", "rivium-chat-android", sdkVersion)

    pom {
        name.set("Rivium Chat Android SDK")
        description.set("Real-time messaging SDK for Android with WebSocket-based chat, read receipts, typing indicators, and presence.")
        inceptionYear.set("2025")
        url.set("https://rivium.co/cloud/rivium-chat")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("rivium")
                name.set("Rivium")
                email.set("founder@rivium.co")
                url.set("https://rivium.co")
            }
        }

        scm {
            url.set("https://github.com/Rivium-co/rivium-chat-android-sdk")
            connection.set("scm:git:git://github.com/Rivium-co/rivium-chat-android-sdk.git")
            developerConnection.set("scm:git:ssh://git@github.com/Rivium-co/rivium-chat-android-sdk.git")
        }
    }
}

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // Centrifuge (WebSocket client for Centrifugo)
    implementation("io.github.centrifugal:centrifuge-java:0.3.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    // Android ships a stub org.json for JVM unit tests; this is the real one.
    testImplementation("org.json:json:20231013")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
