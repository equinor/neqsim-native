# Lightweight base image for dev containers.
# GraalVM 25 (with native-image) is installed by the devcontainer feature in devcontainer.json.
FROM mcr.microsoft.com/devcontainers/base:bullseye

# Packages required by GraalVM native-image (zlib) and for compiling C/C++ examples (build-essential).
RUN apt-get update && \
    export DEBIAN_FRONTEND=noninteractive && \
    apt-get -y install --no-install-recommends \
        build-essential \
        zlib1g-dev \
        curl \
        git \
    && apt-get clean && rm -rf /var/lib/apt/lists/*