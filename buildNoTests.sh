#!/bin/bash
./gradlew clean downloadUrlDependencies build -x test shadowJar
