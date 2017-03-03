#!/bin/sh

echo "Deleting stroom-app-client/src/main/ui/stroom"
rm -rf ./stroom-app-client/src/main/webapp/stroom

echo "Deleting stroom-app-client/src/main/gwt-unitCache"
rm -rf ./stroom-app-client/src/main/gwt-unitCache

echo "Deleting stroom-app-client/target/gwt-unitCache"
rm -rf ./stroom-app-client/target/gwt-unitCache

echo "Deleting stroom-app-client/gwt-DevMode"
rm -rf ./stroom-app-client/gwt-DevMode

echo "Deleting */target"
rm -rf ./*/target
