plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Signature release : fournie par la CI via des variables d'environnement (le keystore n'est
// jamais dans le dépôt). Sans ANDROID_KEYSTORE_PATH, l'APK release est produit non signé.
val releaseKeystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH")

// Envoi d'un avis (SPEC § 14.5) : URL du webhook Discord, fournie par la CI (secret
// DISCORD_WEBHOOK_URL) au seul build release, jamais dans le dépôt. Absente → chaîne vide et section
// « Ton avis » masquée ; mal formée → build en échec, sans afficher l'URL.
val feedbackWebhookUrl = providers.environmentVariable("DISCORD_WEBHOOK_URL").map { it.trim() }.getOrElse("")
if (feedbackWebhookUrl.isNotEmpty() &&
    !Regex("""https://((canary|ptb)\.)?discord(app)?\.com/api/webhooks/\d+/[A-Za-z0-9_-]+""").matches(feedbackWebhookUrl)
) {
    throw GradleException("DISCORD_WEBHOOK_URL n'est pas une URL de webhook Discord (https://discord.com/api/webhooks/<id>/<jeton>)")
}

android {
    namespace = "com.musclenote.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.musclenote.android"
        minSdk = 26
        targetSdk = 37
        // La CI passe -PversionCode=<run_number> -PversionName=0.1.<run_number>.
        versionCode = providers.gradleProperty("versionCode").map { it.toInt() }.getOrElse(1)
        versionName = providers.gradleProperty("versionName").getOrElse("0.1.0-dev")
        buildConfigField("String", "FEEDBACK_WEBHOOK_URL", "\"$feedbackWebhookUrl\"")
    }

    signingConfigs {
        if (releaseKeystorePath.isPresent) {
            create("release") {
                storeFile = file(releaseKeystorePath.get())
                storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // Version de l'app dans les rapports de plantage (§ 10.9), URL du webhook des avis (§ 14.5).
        buildConfig = true
    }

    androidResources {
        localeFilters += listOf("fr", "en")
    }

    testOptions {
        // Les tests JVM touchent du code qui journalise via android.util.Log.
        unitTests.isReturnDefaultValues = true
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.reorderable)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
