#!/bin/bash
#exit script on any error
set -o errexit

DOCKER_REPO="gchq/stroom"
DATE_ONLY="$(date +%Y%m%d)"
DATE_TIME="$(date +%Y%m%d%H%M%S)"
FLOATING_TAG=""
SPECIFIC_TAG=""
#This is a whitelist of branches to produce docker builds for
BRANCH_WHITELIST_REGEX='(^dev$|^master$|^v[0-9].*$)'
doDockerBuild=false

#establish what version of stroom we are building
if [ -n "$TRAVIS_TAG" ]; then
    STROOM_VERSION="${TRAVIS_TAG}"
else
    #No tag so use the branch name as the version
    STROOM_VERSION="${TRAVIS_BRANCH}"
fi

if [ "$TRAVIS_EVENT_TYPE" = "cron" ]; then
    echo "This is a cron build so tag it"
    #This is a cron triggered build so tag as -DAILY and push a tag to git
    versionStr="${STROOM_VERSION}-${DATE_ONLY}-DAILY"
    echo "versionStr: [${versionStr}]"
    SPECIFIC_TAG="--tag=${DOCKER_REPO}:${versionStr}"
    FLOATING_TAG="--tag=${DOCKER_REPO}:${STROOM_VERSION}-SNAPSHOT"
    gitTag=${versionStr}

    git config --global user.email "builds@travis-ci.com"
    git config --global user.name "Travis CI"

    #git tag ${gitTag} -a -m "Automated Travis build $TRAVIS_BUILD_NUMBER" 2>/dev/null
    echo "Tagging commit ${TRAVIS_COMMIT} with tag ${gitTag}"
    git tag -a ${gitTag} ${TRAVIS_COMMIT} -m "Automated Travis build $TRAVIS_BUILD_NUMBER" 
    #git push -q https://$TAGPERM@github.com/gchq/stroom --follow-tags >/dev/null 2>&1
    git push -q https://$TAGPERM@github.com/gchq/stroom --follow-tags 
    doDockerBuild=true
elif [ -n "$TRAVIS_TAG" ]; then
    SPECIFIC_TAG="--tag=${DOCKER_REPO}:${TRAVIS_TAG}"
    doDockerBuild=true
elif [[ "$TRAVIS_BRANCH" =~ $BRANCH_WHITELIST_REGEX ]]; then
    FLOATING_TAG="--tag=${DOCKER_REPO}:${STROOM_VERSION}-SNAPSHOT"
    doDockerBuild=true
fi

echo "Branch: [${TRAVIS_BRANCH}]"
echo "SPECIFIC DOCKER TAG: [${SPECIFIC_TAG}]"
echo "FLOATING DOCKER TAG: [${FLOATING_TAG}]"
echo "TRAVIS_PULL_REQUEST: [${TRAVIS_PULL_REQUEST}]"
echo "doDockerBuild: [${doDockerBuild}]"

#Don't do a docker build for pull requests
if [ "$doDockerBuild" = true ] && [ "$TRAVIS_PULL_REQUEST" = "false" ] ; then
    echo "Building a docker image with tags: ${SPECIFIC_TAG} ${FLOATING_TAG}"

    echo "Copying kafka clinet jar into stroom-app so the docker build can access it"
    #cp $TRAVIS_BUILD_DIR/stroom-kafka-client-impl_0_10_0_1/build/libs/stroom-kafka-client-impl_0_10_0_1-all.jar $TRAVIS_BUILD_DIR/stroom-app/build/libs/stroom-kafka-client-impl_0_10_0_1-all.jar

    #The username and password are configured in the travis gui
    docker login -u="$DOCKER_USERNAME" -p="$DOCKER_PASSWORD"
    docker build ${SPECIFIC_TAG} ${FLOATING_TAG} stroom-app/docker/.
    docker push gchq/stroom
fi

#Deploy the generated swagger specs and swagger UI (obtained from github) to gh-pages
if [ "$TRAVIS_BRANCH" = "master" ]; then
    echo "Deploying swagger-ui to gh-pages"
    ghPagesDir=$TRAVIS_BUILD_DIR/gh-pages
    swaggerUiCloneDir=$TRAVIS_BUILD_DIR/swagger-ui
    mkdir -p $ghPagesDir
    #copy our generated swagger specs to gh-pages
    cp $TRAVIS_BUILD_DIR/stroom-app/src/main/resources/ui/swagger/swagger.* $ghPagesDir/
    #clone swagger-ui repo so we can get the ui html/js/etc
    git clone --depth 1 https://github.com/swagger-api/swagger-ui.git $swaggerUiCloneDir
    #copy the bits of swagger-ui that we need
    cp -r $swaggerUiCloneDir/dist/* $ghPagesDir/
    #repalce the default swagger spec url in swagger UI
    sed -i 's#url: ".*"#url: "https://gchq.github.io/stroom/swagger.json"#g' $ghPagesDir/index.html
fi


