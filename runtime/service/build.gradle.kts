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
}

android {
    namespace = "com.github.yumeyucca.yumebox.runtime.service"

    // KimiNoBox: host JVM unit tests (profile commit / versions / config tester) live in `test/`,
    // mirroring the flat `src/` layout the root build applies to `main`.
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
    implementation(project(":data"))
    implementation(project(":runtime:api"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.xz)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    val mmkvVersion = libs.versions.mmkv64.get()
    //noinspection AndroidLintUseTomlInstead,AndroidLintNewerVersionAvailable
    implementation("com.tencent:mmkv:$mmkvVersion")

    implementation(libs.timber)
    implementation(libs.libsu.core)
    implementation(libs.libsu.service)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    testImplementation("junit:junit:4.13.2") // KimiNoBox: unit tests for the fork's reliability fixes
}
