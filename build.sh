# Shell Colour constants for use in 'echo -e'
# e.g.  echo -e "My message ${GREEN}with just this text in green${NC}"
# shellcheck disable=SC2034
{
  RED='\033[1;31m'
  GREEN='\033[1;32m'
  YELLOW='\033[1;33m'
  BLUE='\033[1;34m'
  NC='\033[0m' # No Colour
}

# -Dorg.gradle.caching=true
GRADLE_ARGS="-Dorg.gradle.daemon=true -Dorg.gradle.parallel=true -Dorg.gradle.workers.max=24 -Dorg.gradle.configureondemand=true"
GWT_ARGS="-PgwtCompilerWorkers=5 -PgwtCompilerMinHeap=50M -PgwtCompilerMaxHeap=4G"

echo -e "${GREEN}Clean${NC}"
./gradlew \
  ${GRADLE_ARGS} \
  clean

# Do the gradle build
# Use custom gwt compile jvm settings to avoid blowing the ram limit in
# travis. At time of writing a sudo VM in travis has 7.5gb ram.
# Each work will chew up the maxHeap value and we have to allow for
# our docker services as well.
# Don't clean as this is a fresh clone and clean will wipe the cached
# content pack zips
echo -e "${GREEN}Do the basic java build${NC}"
./gradlew \
  ${GRADLE_ARGS} \
  --scan \
  --stacktrace \
  -PdumpFailedTestXml=true \
  -Pversion="${TRAVIS_TAG}" \
  build \
  -x shadowJar \
  -x resolve \
  -x copyFilesForStroomDockerBuild \
  -x copyFilesForProxyDockerBuild \
  -x buildDistribution

echo -e "${GREEN}Do the UI build${NC}"
./gradlew \
  ${GRADLE_ARGS} \
  --scan \
  --stacktrace \
  ${GWT_ARGS} \
  stroom-ui:copyYarnBuild \
  stroom-app-gwt:gwtCompile \
  stroom-dashboard-gwt:gwtCompile

#echo -e "${GREEN}Do the yarn build${NC}"
#./gradlew \
#  ${GRADLE_ARGS} \
#  --scan \
#  --stacktrace \
#  stroom-ui:copyYarnBuild
#
## Compile the application GWT UI
#echo -e "${GREEN}Do the GWT app compile${NC}"
#./gradlew \
#  ${GRADLE_ARGS} \
#  --scan \
#  --stacktrace \
#  ${GWT_ARGS} \
#  stroom-app-gwt:gwtCompile
#
## Compile the dashboard GWT UI
#echo -e "${GREEN}Do the GWT dashboard compile${NC}"
#./gradlew \
#  ${GRADLE_ARGS} \
#  --scan \
#  --stacktrace \
#  ${GWT_ARGS} \
#  stroom-dashboard-gwt:gwtCompile

# Make the distribution.
echo -e "${GREEN}Build the distribution${NC}"
./gradlew \
  ${GRADLE_ARGS} \
  --scan \
  --stacktrace \
  -PdumpFailedTestXml=true \
  -Pversion="${TRAVIS_TAG}" \
  shadowJar \
  buildDistribution \
  copyFilesForStroomDockerBuild \
  copyFilesForProxyDockerBuild \
  -x test \
  -x stroom-ui:copyYarnBuild \
  -x stroom-app-gwt:gwtCompile \
  -x stroom-dashboard-gwt:gwtCompile \
  "${extraBuildArgs[@]}"