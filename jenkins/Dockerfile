FROM jenkins/jenkins:lts

USER root

# install Docker CLI + docker compose plugin inside the Jenkins container
RUN apt-get update && apt-get install -y \
    ca-certificates curl gnupg lsb-release && \
    mkdir -p /etc/apt/keyrings && \
    curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg && \
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian $(lsb_release -cs) stable" > /etc/apt/sources.list.d/docker.list && \
    apt-get update && \
    apt-get install -y docker-ce-cli docker-compose-plugin && \
    apt-get clean

# also need Maven for building (or the pipeline can use a Maven container)
RUN apt-get install -y maven

ARG SONAR_SCANNER_VERSION=8.1.0.6389
RUN case "$(dpkg --print-architecture)" in \
        arm64) suffix=linux-aarch64 ;; \
        *) suffix=linux-x64 ;; \
    esac && \
    curl -fsSL -o /tmp/sonar-scanner.zip \
        "https://binaries.sonarsource.com/Distribution/sonar-scanner-cli/sonar-scanner-cli-${SONAR_SCANNER_VERSION}-${suffix}.zip" && \
    unzip -q /tmp/sonar-scanner.zip -d /opt && \
    mv "/opt/sonar-scanner-${SONAR_SCANNER_VERSION}-${suffix}" /opt/sonar-scanner && \
    ln -s /opt/sonar-scanner/bin/sonar-scanner /usr/local/bin/sonar-scanner && \
    rm /tmp/sonar-scanner.zip

USER jenkins