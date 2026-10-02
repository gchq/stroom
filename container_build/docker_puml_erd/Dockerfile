# We need debian for glibc as required by plant_erd
FROM debian:trixie-slim@sha256:a99cfc517144bc59b1978475ec53b46ecabec7e43635402ee5b77cc54cd1b20a

ARG DOCKER_HOST
ENV DOCKER_HOST=$DOCKER_HOST
ARG PLANT_ERD_VERSION=v0.4.12

WORKDIR /builder

# Install curl + ca-certificates, download binary, then clean up apt cache
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    curl \
  && curl -Ls https://github.com/sue445/plant_erd/releases/download/${PLANT_ERD_VERSION}/plant_erd_linux_amd64 -o plant_erd_linux_amd64 \
  && chmod u+x plant_erd_linux_amd64 \
  && apt-get purge -y --auto-remove curl \
  && rm -rf /var/lib/apt/lists/* \
  && echo "alias ll='ls -la'" >> /etc/bash.bashrc

CMD [ "bash", "-c", "./plant_erd_linux_amd64 mysql --host $DOCKER_HOST --port 3307 --database stroom --password my-secret-pw" ]
