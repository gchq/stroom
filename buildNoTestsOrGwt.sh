#!/bin/bash
./gradlew clean downloadUrlDependencies build -x test -x gwtCompile -x generateSwaggerDocumentation shadowJar "$@"
