# Need glibc for plant_erd_linux_amd64
FROM frolvlad/alpine-glibc:alpine-3.22_glibc-2.42@sha256:248933011db7eae9c848f90800edb8aad79cf3ab8b617e45ea1c256a83f2a43b

ARG DOCKER_HOST
ENV DOCKER_HOST=$DOCKER_HOST
ARG PLANT_ERD_VERSION=v0.4.4

RUN apk add --no-cache \
    bash \
    curl \
  && mkdir /builder \
  && curl -Ls https://github.com/sue445/plant_erd/releases/download/$PLANT_ERD_VERSION/plant_erd_linux_amd64 -o /builder/plant_erd_linux_amd64 \
  && chmod u+x /builder/plant_erd_linux_amd64 \
  && alias ll='ls -l' \
  && apk del curl

CMD [ "bash", "-c", "./plant_erd_linux_amd64 mysql --host $DOCKER_HOST --port 3307 --database stroom --password my-secret-pw" ]

WORKDIR /builder
