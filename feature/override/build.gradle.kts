/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c)  YumeYucca 2025 - Present
 *
 */

plugins {
    id("com.android.library")
    kotlin("plugin.serialization")
    kotlin("plugin.compose")
}

android {
    namespace = "com.github.yumeyucca.yumebox.feature.override"

    buildFeatures {
        compose = true
    }

    // KimiNoBox: host JVM unit tests of the node source templates live in `test/`, mirroring the
    // flat `src/` layout the root build applies to `main`.
    sourceSets {
        getByName("test") {
            kotlin.directories.apply {
                clear()
                add("test")
            }
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":common"))
    implementation(project(":locale"))
    implementation(project(":ui"))
    implementation(project(":data"))
    implementation(project(":runtime:api"))
    implementation(project(":runtime:client"))
    implementation(project(":feature:editor"))

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.timber)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.reorderable)

    // KimiNoBox: node source templates. SnakeYAML Engine 3.x reads emoji names, which the
    // SnakeYAML 1.18 behind upstream's YamlCodec rejects.
    implementation("org.snakeyaml:snakeyaml-engine:3.1.1")
    testImplementation("junit:junit:4.13.2") // KimiNoBox
}
