#!/bin/bash
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
    #This is a cron triggered build so tag as -DAILY and push a tag to git
    versionStr="${STROOM_VERSION}-${DATE_ONLY}-DAILY"
    echo "versionStr: ${versionStr}"
    SPECIFIC_TAG="--tag=${DOCKER_REPO}:${versionStr}"
    echo "SPECIFIC_TAG: ${SPECIFIC_TAG}"
    FLOATING_TAG="--tag=${DOCKER_REPO}:${STROOM_VERSION}-SNAPSHOT"
    echo "FLOATING_TAG: ${FLOATING_TAG}"
    gitTag=${versionStr}
    echo "gitTag: ${gitTag}"
    echo "commit hash: ${TRAVIS_COMMIT}"

    git config --global user.email "builds@travis-ci.com"
    git config --global user.name "Travis CI"

    #git tag ${gitTag} -a -m "Automated Travis build $TRAVIS_BUILD_NUMBER" 2>/dev/null
    git tag -a ${gitTag} ${TRAVIS_COMMIT} -m "Automated Travis build $TRAVIS_BUILD_NUMBER" 
    #git push -q https://$TAGPERM@github.com/gchq/stroom --follow-tags >/dev/null 2>&1
    git push -q https://$TAGPERM@github.com/gchq/stroom --follow-tags 
    doDockerBuild=true
elif [ -n "$TRAVIS_TAG" ]; then
    SPECIFIC_TAG="--tag=${DOCKER_REPO}:${TRAVIS_TAG}"
    echo "SPECIFIC_TAG: ${SPECIFIC_TAG}"
    doDockerBuild=true
elif [[ "$TRAVIS_BRANCH" =~ $BRANCH_WHITELIST_REGEX ]]; then
    FLOATING_TAG="--tag=${DOCKER_REPO}:${STROOM_VERSION}-SNAPSHOT"
    echo "FLOATING_TAG: ${FLOATING_TAG}"
    doDockerBuild=true
fi

#Don't do a docker build for pull requests
if [ "$doDockerBuild" = true ] && [ "$TRAVIS_PULL_REQUEST" = "false" ] ; then
    echo "Using tags: ${SPECIFIC_TAG} ${FLOATING_TAG}"

    #The username and password are configured in the travis gui
    docker login -u="$DOCKER_USERNAME" -p="$DOCKER_PASSWORD"
    docker build ${SPECIFIC_TAG} ${FLOATING_TAG} stroom-app/. 
    docker push gchq/stroom
fi


