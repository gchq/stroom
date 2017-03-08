#!/bin/sh

echo "Clearing out build/stroom-app"
rm -rf ./build/stroom-app

echo "unzipping distribution"
distFile=build/distributions/stroom-app-distribution-*.zip
if [ ! -f $distFile ]; then
    echo "No distribution zip file present"
    exit 1
fi

unzip $distFile -d build

#stop/remove any existing containers/images
docker stop stroom-dev
docker rm stroom-dev
docker rmi stroom-dev

#Allow for running behind a proxy or not
if [ -z $HTTP_PROXY ]; then
    proxyArg1=""
else
    proxyArg1="--build-arg http_proxy=$HTTP_PROXY"
fi

if [ -z $HTTPS_PROXY ]; then
    proxyArg2=""
else
    proxyArg2="--build-arg https_proxy=$HTTPS_PROXY"
fi

echo "proxyArg1: $proxyArg1"
echo "proxyArg2: $proxyArg2"


docker build ${proxyArg1} ${proxyArg2} --tag=stroom-dev:latest build/stroom-app

#This command assumes that the database jdbc url, username and password are all defined in properties in ~/.stroom/stroom.conf else
#add something like the following to the run command
#-e STROOM_JDBC_DRIVER_URL="jdbc:mysql://stroom-db/stroom? useUnicode=yes&characterEncoding=UTF-8" -e STROOM_JDBC_DRIVER_USERNAME="stroomuser" -e STROOM_JDBC_DRIVER_PASSWORD="stroompassword1"
docker run -p 8080:8080 --link stroom-db -v ~/.stroom:/root/.stroom --name=stroom-dev stroom-dev
