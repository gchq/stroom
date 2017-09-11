#!/bin/bash

mkdir -p ~/.stroom
mkdir -p ~/.stroom/plugins
mkdir -p /tmp/stroom
cp ./stroom.conf.example ~/.stroom/stroom.conf
echo "stroom.plugins.lib.dir=$HOME/.stroom/plugins" >> ~/.stroom/stroom.conf