#!/bin/bash
echo "Building the Server, without GWT compile at all, run Draft Compile later"
./gradlew clean build -x integrationTest -x gwtCompile "$@"

echo "Now running GWT draft compile, only on the main app"
./gradlew :stroom-app-gwt:gwtDraftCompile

