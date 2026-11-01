plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.fakenews.comics"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fakenews.comics"
        minSdk = 30
        targetSdk = 36
        versionCode = 8
        versionName = "0.1.0"
        // 不显式指定会回落到已废弃的 android.test.InstrumentationTestRunner，
        // 它找不到 JUnit4 的 @Test，表现为「0 tests + 进程崩溃」
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // 自托管签名：密钥在仓库外，口令在 ~/.gradle/gradle.properties。
        // 缺任一属性时 release 变体不签名，克隆出来的机器仍可装 debug 包。
        create("release") {
            val path = providers.gradleProperty("fakenewsStoreFile").orNull
            val storePw = providers.gradleProperty("fakenewsStorePassword").orNull
            val alias = providers.gradleProperty("fakenewsKeyAlias").orNull
            val keyPw = providers.gradleProperty("fakenewsKeyPassword").orNull
            if (path != null && storePw != null && alias != null && keyPw != null) {
                storeFile = file(path)
                storePassword = storePw
                keyAlias = alias
                keyPassword = keyPw
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
            signingConfigs.findByName("release")
                ?.takeIf { it.storeFile != null }
                ?.let { signingConfig = it }
        }
        // 只给 baseline profile 采集用：代码路径与 release 相同，但保留符号，
        // 否则生成的规则全是混淆名，R8 重写时匹配不上会被整段丢弃。
        create("benchmark") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        // 「我的」页要显示客户端版本号（VERSION_NAME / VERSION_CODE）
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

/**
 * Compose 编译器指标。
 *
 * 只产出文本报告（`build/compose-reports`），不进包、不影响运行时 —— 排「列表/页面
 * 为什么在重组」时用它代替猜：`*-composables.txt` 里 `skippable` 那一列能直接指出
 * 哪些 Composable 每次重组都会重跑，`*-classes.txt` 则列出被判为 unstable 的类型
 * （通常是带 `List` 字段的 DTO）。
 */
composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose-reports")
    metricsDestination = layout.buildDirectory.dir("compose-metrics")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.security:security-crypto:1.0.0")
    // 让打包进去的 baseline profile 在安装后自动生效（无需系统级 profile）
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    implementation("io.coil-kt:coil-compose:2.7.0")

    // 底栏毛玻璃（backdrop blur）。1.1.0 是最后一批对 Compose 1.7.1 / Kotlin 2.0.21 的版本；
    // 再往上（1.2+）要求 Kotlin 2.1，会连带升 stdlib。
    // 1.1.0 是实测最优：模糊层扩边区会用 backgroundColor 预填充，屏底沿的衰减带最浅。
    // 试过 1.6.10（#708 的修复版）：重写后不再用 backgroundColor 填充扩边，底沿反而退回
    // 近原色（2026-10-28 模拟器扫色实测），不要升级。Web(Chromium) 底沿同样有衰减带，
    // 见 docs/2026-10-27-AI接手说明.md §7.12。
    implementation("dev.chrisbanes.haze:haze:1.1.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    // instrumented UI 测试：ui-test-manifest 进 debug 即可（只在 debug 包里补测试用的 Activity）
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
