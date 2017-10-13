#!/bin/bash

#exit script on any error
set -e

DOCKER_REPO="gchq/stroom"
GITHUB_REPO="gchq/stroom"
DOCKER_CONTEXT_ROOT="stroom-app/docker/."
FLOATING_TAG=""
SPECIFIC_TAG=""
#This is a whitelist of branches to produce docker builds for
BRANCH_WHITELIST_REGEX='(^dev$|^master$|^v[0-9].*$)'
doDockerBuild=false

#Shell Colour constants for use in 'echo -e'
#e.g.  echo -e "My message ${GREEN}with just this text in green${NC}"
RED='\033[1;31m'
GREEN='\033[1;32m'
YELLOW='\033[1;33m'
BLUE='\033[1;34m'
NC='\033[0m' # No Colour 

createGitTag() {
    tagName=$1
    
    git config --global user.email "builds@travis-ci.com"
    git config --global user.name "Travis CI"

    echo -e "Tagging commit [${GREEN}${TRAVIS_COMMIT}${NC}] with tag [${GREEN}${tagName}${NC}]"
    git tag -a ${tagName} ${TRAVIS_COMMIT} -m "Automated Travis build $TRAVIS_BUILD_NUMBER" 
    #TAGPERM is a travis encrypted github token, see 'env' section in .travis.yml
    git push -q https://$TAGPERM@github.com/${GITHUB_REPO} ${tagName}
}


#establish what version of stroom we are building
if [ -n "$TRAVIS_TAG" ]; then
    #Tagged commit so use that as our stroom version, e.g. v6.0.0
    STROOM_VERSION="${TRAVIS_TAG}"
else
    #No tag so use the branch name as the version, e.g. dev
    STROOM_VERSION="${TRAVIS_BRANCH}"
fi

#Dump all the travis env vars to the console for debugging
echo -e "TRAVIS_BUILD_NUMBER: [${GREEN}${TRAVIS_BUILD_NUMBER}${NC}]"
echo -e "TRAVIS_COMMIT:       [${GREEN}${TRAVIS_COMMIT}${NC}]"
echo -e "TRAVIS_BRANCH:       [${GREEN}${TRAVIS_BRANCH}${NC}]"
echo -e "TRAVIS_TAG:          [${GREEN}${TRAVIS_TAG}${NC}]"
echo -e "TRAVIS_PULL_REQUEST: [${GREEN}${TRAVIS_PULL_REQUEST}${NC}]"
echo -e "TRAVIS_EVENT_TYPE:   [${GREEN}${TRAVIS_EVENT_TYPE}${NC}]"
echo -e "STROOM_VERSION:      [${GREEN}${STROOM_VERSION}${NC}]"

if [ "$TRAVIS_EVENT_TYPE" = "cron" ]; then
    echo "This is a cron build so just tag the commit and exit"
    echo "The build will happen when travis picks up the tagged commit"
    #This is a cron triggered build so tag as -DAILY and push a tag to git
    DATE_ONLY="$(date +%Y%m%d)"
    gitTag="${STROOM_VERSION}-${DATE_ONLY}-DAILY"

    createGitTag ${gitTag}
else
    #Do the gradle build
    # Use 1 local worker to avoid using too much memory as each worker will chew up ~500Mb ram
    ./gradlew -Pversion=$TRAVIS_TAG -PgwtCompilerWorkers=1 -PgwtCompilerMinHeap=50M -PgwtCompilerMaxHeap=500M clean build

    if [ -n "$TRAVIS_TAG" ]; then
        #This is a tagged commit, so create a docker image with that tag
        SPECIFIC_TAG="--tag=${DOCKER_REPO}:${TRAVIS_TAG}"
        doDockerBuild=true
    elif [[ "$TRAVIS_BRANCH" =~ $BRANCH_WHITELIST_REGEX ]]; then
        #This is a branch we want to create a snapshot docker image for
        FLOATING_TAG="--tag=${DOCKER_REPO}:${STROOM_VERSION}-SNAPSHOT"
        doDockerBuild=true
    fi

    echo -e "SPECIFIC DOCKER TAG: [${GREEN}${SPECIFIC_TAG}${NC}]"
    echo -e "FLOATING DOCKER TAG: [${GREEN}${FLOATING_TAG}${NC}]"
    echo -e "doDockerBuild:       [${GREEN}${doDockerBuild}${NC}]"

    #Don't do a docker build for pull requests
    if [ "$doDockerBuild" = true ] && [ "$TRAVIS_PULL_REQUEST" = "false" ] ; then
        echo -e "Building a docker image with tags: ${GREEN}${SPECIFIC_TAG}${NC} ${GREEN}${FLOATING_TAG}${NC}"

        #The username and password are configured in the travis gui
        docker login -u="$DOCKER_USERNAME" -p="$DOCKER_PASSWORD"
        docker build ${SPECIFIC_TAG} ${FLOATING_TAG} ${DOCKER_CONTEXT_ROOT}
        docker push ${DOCKER_REPO}
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
fi

exit 0
