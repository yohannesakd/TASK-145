# Stage 1: Android SDK base
FROM eclipse-temurin:17-jdk-jammy AS sdk-base

ENV ANDROID_HOME=/opt/android-sdk
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/build-tools/34.0.0:${PATH}"

# Install required system packages
RUN apt-get update && apt-get install -y --no-install-recommends \
        curl \
        unzip \
        git \
    && rm -rf /var/lib/apt/lists/*

# Download and install Android command-line tools
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
        -o /tmp/cmdline-tools.zip && \
    unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_HOME}/cmdline-tools && \
    mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

# Accept licences and install SDK components
RUN yes | sdkmanager --licenses > /dev/null 2>&1 && \
    sdkmanager --update && \
    sdkmanager \
        "platform-tools" \
        "platforms;android-34" \
        "build-tools;34.0.0"

# On aarch64 hosts the SDK ships x86_64 binaries that cannot execute natively.
# Download a community-built aarch64 aapt2 static binary from AOSP source and
# overwrite the SDK copy so the Gradle override in gradle.properties works on
# both amd64 and arm64 Docker hosts.
RUN ARCH="$(uname -m)" && \
    if [ "$ARCH" = "aarch64" ]; then \
        curl -fsSL -L https://github.com/lzhiyong/android-sdk-tools/releases/download/34.0.3/android-sdk-tools-static-aarch64.zip \
            -o /tmp/sdk-tools-arm64.zip && \
        unzip -q -o -j /tmp/sdk-tools-arm64.zip build-tools/aapt2 -d ${ANDROID_HOME}/build-tools/34.0.0/ && \
        chmod +x ${ANDROID_HOME}/build-tools/34.0.0/aapt2 && \
        rm /tmp/sdk-tools-arm64.zip; \
    fi

WORKDIR /project

# Stage 2: Build the APK and run tests + lint
FROM sdk-base AS builder
COPY . .

RUN chmod +x gradlew && \
    ./gradlew assembleDebug test lint --no-daemon --stacktrace

# Copy the APK to a well-known output location
RUN mkdir -p /output && \
    cp app/build/outputs/apk/debug/app-debug.apk /output/app-debug.apk && \
    sha256sum /output/app-debug.apk | awk '{print $1}' > /output/app-debug.apk.sha256

# Stage 3: Lightweight server to download the APK
FROM python:3.12-slim
WORKDIR /serve
COPY --from=builder /output/ .
EXPOSE 8000
CMD ["python", "-m", "http.server", "8000"]
