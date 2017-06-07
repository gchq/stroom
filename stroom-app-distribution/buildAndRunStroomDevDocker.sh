#!/bin/sh

echo "Clearing out target/stroom-app"
rm -rf ./target/stroom-app

echo "unzipping distribution"
distFile=target/stroom-app-distribution-*-bin.zip
if [ ! -f $distFile ]; then
    echo "No distribution zip file present"
    exit 1
fi

unzip $distFile -d target

#stop/remove any existing containers/images
docker stop stroom-test-db
docker rm stroom-test-db
docker stop stroom-test-stats-db
docker rm stroom-test-stats-db

docker stop stroom
docker rm stroom
docker rmi stroom

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


docker build ${proxyArg1} ${proxyArg2} --tag=stroom:latest target/stroom-app

#This command assumes that the database jdbc url, username and password are all defined in properties in ~/.stroom/stroom.conf else
#add something like the following to the run command
#-e STROOM_JDBC_DRIVER_URL="jdbc:mysql://stroom-db/stroom? useUnicode=yes&characterEncoding=UTF-8" -e STROOM_JDBC_DRIVER_USERNAME="stroomuser" -e STROOM_JDBC_DRIVER_PASSWORD="stroompassword1"
# docker run -p 8080:8080 --link stroom-db --link stroom-stats-db -v ~/.stroom:/root/.stroom --name=stroom -e STROOM_JDBC_DRIVER_URL="jdbc:mysql://stroom-db/stroom?useUnicode=yes&characterEncoding=UTF-8" -e STROOM_JDBC_DRIVER_USERNAME="stroomuser" -e STROOM_JDBC_DRIVER_PASSWORD="stroompassword1" -e STROOM_STATISTICS_SQL_JDBC_DRIVER_URL="jdbc:mysql://stroom-stats-db/statistics?useUnicode=yes&characterEncoding=UTF-8" -e STROOM_STATISTICS_SQL_JDBC_DRIVER_USERNAME="stroomuser" -e STROOM_STATISTICS_SQL_JDBC_DRIVER_PASSWORD="stroompassword1" stroom


docker run --name stroom-test-db -e MYSQL_ROOT_PASSWORD=my-secret-pw -e MYSQL_USER=stroomuser -e MYSQL_PASSWORD=stroompassword1 -e MYSQL_DATABASE=stroom -d mysql:5.6
docker run --name stroom-test-stats-db -e MYSQL_ROOT_PASSWORD=my-secret-pw -e MYSQL_USER=stroomuser -e MYSQL_PASSWORD=stroompassword1 -e MYSQL_DATABASE=statistics -d mysql:5.6
docker run -p 8080:8080 --link stroom-test-db --link stroom-test-stats-db --name=stroom -e STROOM_JDBC_DRIVER_URL="jdbc:mysql://stroom-test-db/stroom?useUnicode=yes&characterEncoding=UTF-8" -e STROOM_JDBC_DRIVER_USERNAME="stroomuser" -e STROOM_JDBC_DRIVER_PASSWORD="stroompassword1" -e STROOM_STATISTICS_SQL_JDBC_DRIVER_URL="jdbc:mysql://stroom-test-stats-db/statistics?useUnicode=yes&characterEncoding=UTF-8" -e STROOM_STATISTICS_SQL_JDBC_DRIVER_USERNAME="stroomuser" -e STROOM_STATISTICS_SQL_JDBC_DRIVER_PASSWORD="stroompassword1" stroom