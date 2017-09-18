#!/bin/bash
./gradlew clean build -x integrationTest -x gwtCompile "$@"

