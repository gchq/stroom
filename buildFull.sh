#!/bin/bash

./gradlew clean build "$@"

echo "Copying over the Kafka 0.10.0.1 provider library"
cp ~/git/stroom/stroom-kafka-client-impl_0_10_0_1/build/libs/stroom-kafka-client-impl_0_10_0_1-all.jar ~/.stroom/plugins/
