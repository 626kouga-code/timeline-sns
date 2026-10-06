plugins {
    java
    checkstyle
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.github.spotbugs") version "6.5.12"
}

group = "com.timelinesns"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.flywaydb:flyway-database-postgresql")
    // 画像の保存先（本番は S3、ローカル・テストは S3 互換の S3Mock）
    implementation(platform("software.amazon.awssdk:bom:2.55.11"))
    implementation("software.amazon.awssdk:s3")
    // 画像処理。ImageIO に WebP の読み込みを足し、Exif の向き（Orientation）を読む
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.15.2")
    implementation("com.drewnoakes:metadata-extractor:2.21.0")
    runtimeOnly("org.postgresql:postgresql")
    compileOnly("com.github.spotbugs:spotbugs-annotations:4.10.4")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("com.adobe.testing:s3mock-testcontainers:5.2.3")
    // テストで実行 SQL を数え、N+1 が起きていないことを確かめる（support/QueryCounter）
    testImplementation("net.ttddyy:datasource-proxy:1.10.1")
    testCompileOnly("com.github.spotbugs:spotbugs-annotations:4.10.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// ローカルで bootRun するときは開発用の設定（application-local.yml）を使う
tasks.bootRun {
    systemProperty("spring.profiles.active", "local")
}

checkstyle {
    toolVersion = "14.3.0"
    configFile = file("config/checkstyle/checkstyle.xml")
    maxWarnings = 0
    isIgnoreFailures = false
}

spotbugs {
    toolVersion = "4.10.4"
    excludeFilter = file("config/spotbugs/exclude.xml")
    ignoreFailures = false
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports.create("html") { required = true }
}
