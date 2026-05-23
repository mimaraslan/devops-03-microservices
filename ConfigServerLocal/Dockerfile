# Çok aşamalı (multi-stage) build:
# 1. aşamada uygulama derlenir, 2. aşamada yalnızca çalıştırılır.
# Son imajda derleyici ve kaynak kod olmaz; imaj boyutu küçülür.

# --- Derleme aşaması (build) ---
# JDK (Java Development Kit): javac, Gradle vb. için gerekli.
# Kaynak koddan bootJar üretmek derleme ortamı ister; runtime için JDK şart değil.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Önce bağımlılık tanımları — kaynak değişince bu katmanlar önbellekte kalabilir
COPY gradle/ ./gradle/
COPY gradlew ./
COPY gradlew.bat ./
COPY build.gradle ./
COPY settings.gradle ./

# gradlew Windows CRLF satır sonları Linux'ta #!/bin/sh ile uyumsuz (exit 127)
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# Spring Cloud Config Server modülü
COPY ConfigServerLocal/ ./ConfigServerLocal/

# Gradle daemon kapalı; konteyner içinde daemon gereksiz bellek tüketir
ENV JAVA_TOOL_OPTIONS="-Xmx1536m -XX:MaxMetaspaceSize=512m"
RUN ./gradlew :ConfigServerLocal:bootJar -x test --no-daemon

# --- Çalışma zamanı aşaması (runtime) ---
# JRE (Java Runtime Environment): yalnızca java -jar ile JAR çalıştırır.
# JDK'dan küçüktür; derleyici ve geliştirme araçları final imajda yoktur.
FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app

# Derleme aşamasından yalnızca üretilen JAR kopyalanır
COPY --from=build /app/ConfigServerLocal/build/libs/*.jar application.jar

EXPOSE 8888
ENTRYPOINT ["java","-jar","application.jar"]
