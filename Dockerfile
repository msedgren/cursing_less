FROM eclipse-temurin:21 AS cursing_less_jdk

ENV USER=jdk_user
ENV HOME=/home/$USER
RUN usermod --login $USER ubuntu --move-home --home /home/$USER\
     && groupmod --new-name $USER ubuntu
USER $USER
WORKDIR $HOME

COPY --chown=$USER README.md CHANGELOG.md gradlew gradle.properties build.gradle.kts settings.gradle.kts settings-gradle.lockfile qodana.yml build_plugin.sh ./
COPY --chown=$USER gradle ./gradle

FROM cursing_less_jdk AS tdd
ENTRYPOINT ["./gradlew", "-t", "test"]

FROM cursing_less_jdk AS build
COPY --chown=$USER src ./src
RUN --mount=type=cache,target=/home/jdk_user/.gradle,uid=1000,gid=1000,rw ./build_plugin.sh

