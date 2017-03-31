#!/bin/bash

function buildIt {
    if [ -f "build.gradle" ]; then
        ./gradlew clean build publishToMavenLocal
    else
        mvn clean install
    fi
}

function sortItAllOut {
    if [[ -d $1 ]]; then
        # Update the repo
        echo -e "Checking repo \e[96m$1\e[0m for updates"
        cd $1
        git pull

        buildIt

        cd ..
    else
        # Clone the repo
        echo -e "Cloning \e[96m$1\e[0m"
        git clone https://github.com/gchq/$1.git
        cd $1

        buildIt

        cd ..
    fi
}

sortItAllOut event-logging
sortItAllOut hadoop-hdfs-shaded
sortItAllOut hadoop-common-shaded
sortItAllOut hbase-common-shaded

mysql -e 'CREATE DATABASE IF NOT EXISTS stroom;'

mkdir ~/.stroom/
# cp ./.travis.stroom.conf ~/.stroom/stroom.conf