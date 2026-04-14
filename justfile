#!/usr/bin/env just --justfile

set quiet

push:
    git add -A && (git diff --quiet HEAD || git commit -am "WIP") && git push origin main

pull:
    git pull

build:
    ./gradlew build

rebuild:
    ./gradlew clean build --refresh-dependencies --rerun-tasks

publish:
    ./gradlew publishToMavenLocal

update-dependencies:
    ./gradlew versionCatalogUpdate

@update-gradle:
    #!/usr/bin/env bash
    set -euo pipefail
    ./gradlew wrapper --gradle-version latest --distribution-type all
    DIST_URL=$(grep distributionUrl gradle/wrapper/gradle-wrapper.properties | cut -d= -f2 | sed 's/\\//g')
    CHECKSUM=$(curl -sL "${DIST_URL}.sha256")
    sed -i '' "s/distributionSha256Sum=.*/distributionSha256Sum=${CHECKSUM}/" gradle/wrapper/gradle-wrapper.properties
    echo "Updated wrapper checksum: ${CHECKSUM}"

update-all:
    just update-dependencies && just update-gradle
