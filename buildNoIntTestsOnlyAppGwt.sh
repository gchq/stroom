#!/bin/bash
./gradlew clean build -x integrationTest -x stroom-dashboard-gwt:gwtCompile "$@"

