plugins {
    // toolchain で指定した JDK 25 が手元になければ自動でダウンロードする
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "backend"
