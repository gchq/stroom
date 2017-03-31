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

# Set up the data base - we need to set up a custom user otherwise we'll have trouble connecting
mysql -e "CREATE DATABASE IF NOT EXISTS stroom;"
mysql -e "CREATE USER 'stroomuser'@'localhost' IDENTIFIED BY 'stroompassword1';"
mysql -e "GRANT ALL PRIVILEGES ON * . * TO 'stroomuser'@'localhost';"
mysql -e "FLUSH PRIVILEGES"