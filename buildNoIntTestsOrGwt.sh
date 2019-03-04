#!/bin/bash
./gradlew clean build -x test -x gwtCompile "$@"

