#!/bin/bash

mkdir -p ~/.stroom
mkdir -p ~/.stroom/plugins
mkdir -p /tmp/stroom
./stroom.conf.sh
echo "stroom.plugins.lib.dir=$HOME/.stroom/plugins" >> ~/.stroom/stroom.conf