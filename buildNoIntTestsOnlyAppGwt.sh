#!/bin/bash
./gradlew clean build -x test -x stroom-dashboard-gwt:gwtCompile "$@"

