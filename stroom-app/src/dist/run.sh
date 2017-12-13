#!/usr/bin/env bash

STROOM_HOME="$HOME/.stroom/"

# Create the ~/.stroom directory if it doesn't exist
if [ ! -d "$STROOM_HOME" ]; then
  echo "Stroom's working directory (~/.stroom) does not exist - creating"
  mkdir $STROOM_HOME
else
  echo "Stroom's working directory (~/.stroom) already exists."
fi


# Always copy the config over - the config here is the master config
echo "Updating the config in Stroom's working directory..."
cp -rf stroom.conf $STROOM_HOME

# Run Stroom
echo "Running Stroom..."
java -jar stroom-app-all.jar server config.yml
