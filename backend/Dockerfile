# syntax=docker/dockerfile:1
# バックエンド（Spring Boot）の本番用イメージ。ECS Fargate の ARM（Graviton）で動かす（docs/12）。
#   docker buildx build --platform linux/arm64 -t timeline-sns-backend backend
# ローカルで動きを確かめるときは docker compose --profile app up -d --build backend（docs/12）

# ① ビルド：jar は CPU の種類によらないので、ビルドする PC の CPU のまま作る（ARM をまねて動かすと遅いため）
FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
# 依存ライブラリの取得を先にしておき、ソースだけ変えたときはキャッシュを使う
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew dependencies --no-daemon --quiet > /dev/null
COPY config config
COPY src src
# テスト・静的解析は CI で済んでいるので、ここでは jar を作るだけ
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon --quiet \
    && java -Djarmode=tools -jar build/libs/*.jar extract --layers --launcher --destination extracted

# ② 実行：JDK ではなく JRE だけのイメージにする
FROM eclipse-temurin:25-jre
WORKDIR /app
# root では動かさない
RUN groupadd --system app && useradd --system --gid app --no-create-home app
# 変わりにくい順（ライブラリ → アプリのコード）に重ね、更新時に送るイメージの差分を小さくする
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
USER app
EXPOSE 8080
# ヒープはコンテナのメモリ（Fargate の 1GB）の 75% まで。残りはスレッド・メタスペースなどに使う
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
