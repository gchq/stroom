#!/bin/bash
CURRENT_STROOM_DEV_VERSION="v6.0.0"
DOCKER_REPO="gchq/stroom"
DATE_ONLY="$(date +%Y%m%d)"
DATE_TIME="$(date +%Y%m%d%H%M%S)"
FLOATING_TAG=""
SPECIFIC_TAG=""

#establish what version of stroom we are building
if [ -n "$TRAVIS_TAG" ]; then
    STROOM_VERSION=${TRAVIS_TAG}
else
    STROOM_VERSION=${CURRENT_STROOM_DEV_VERSION}
fi


if [ "$TRAVIS_EVENT_TYPE" = "cron" ]; then
    #This is a cron triggered build so tag as -DAILY and push a tag to git
    versionStr="${STROOM_VERSION}-${DATE_ONLY}-DAILY"
    echo "versionStr: ${versionStr}"
    SPECIFIC_TAG="--tag=${DOCKER_REPO}:${versionStr}"
    echo "SPECIFIC_TAG: ${SPECIFIC_TAG}"
    gitTag=${versionStr}
    echo "gitTag: ${gitTag}"

    git config --global user.email "builds@travis-ci.com"
    git config --global user.name "Travis CI"

    git status

    #git tag ${gitTag} -a -m "Automated Travis build $TRAVIS_BUILD_NUMBER" 2>/dev/null
    git tag ${gitTag} -a -m "Automated Travis build $TRAVIS_BUILD_NUMBER" 
    #git push -q https://$TAGPERM@github.com/gchq/stroom --follow-tags >/dev/null 2>&1
    git push -q https://$TAGPERM@github.com/gchq/stroom --follow-tags 
elif [ -n "$TRAVIS_TAG" ]; then
    SPECIFIC_TAG="--tag=${DOCKER_REPO}:${TRAVIS_TAG}"
    echo "SPECIFIC_TAG: ${SPECIFIC_TAG}"
elif [ "$TRAVIS_BRANCH" = "dev" ]; then
    FLOATING_TAG="--tag=${DOCKER_REPO}:${STROOM_VERSION}-SNAPSHOT"
    echo "FLOATING_TAG: ${FLOATING_TAG}"
fi


#Do a docker build for git tags, dev branch or cron builds
if [ "$TRAVIS_BRANCH" = "dev" ] || [ -n "$TRAVIS_TAG" ] || [ "$TRAVIS_EVENT_TYPE" = "cron" ]; then
    if [ "$TRAVIS_PULL_REQUEST" = "false" ]; then
        echo "Using tags: ${SPECIFIC_TAG} ${FLOATING_TAG}"

        #The username and password are configured in the travis gui
        docker login -u="$DOCKER_USERNAME" -p="$DOCKER_PASSWORD"
        docker build ${SPECIFIC_TAG} ${FLOATING_TAG} stroom-app/. 
        docker push gchq/stroom
    fi
fi


